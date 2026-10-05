package sprig.compiler.lsp;

import java.io.BufferedInputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileSystemNotFoundException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import sprig.compiler.ast.Decl;
import sprig.compiler.ast.Module;
import sprig.compiler.diag.Diagnostic;
import sprig.compiler.diag.Diagnostics;
import sprig.compiler.diag.Phase;
import sprig.compiler.diag.Span;
import sprig.compiler.front.SourceFormatter;
import sprig.compiler.lsp.SymbolIndex.Occurrence;
import sprig.compiler.lsp.SymbolIndex.Target;
import sprig.compiler.project.Project;
import sprig.compiler.sem.ImportNames;
import sprig.compiler.tooling.Catalog;
import sprig.compiler.tooling.ToolJson;

/**
 * {@code sprig lsp}: a Language Server Protocol server on standard input and
 * output. Every answer comes from the same parser, resolver and checker as
 * {@code sprig check}; when the current text does not get far enough through
 * the compiler, requests return nothing rather than a guess.
 */
public final class LanguageServer {
    private static final long CHANGE_DELAY_MS = 300;
    private static final int MAX_REFERENCE_FILES = 200;
    private static final Object NO_RESPONSE = new Object();
    private static final Object END = new Object();
    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z_][A-Za-z0-9_]*");
    private static final Set<String> SKIPPED_DIRECTORIES = Set.of("build", "sprig-build", "node_modules", "dist",
            "out", "target");

    private static final int REQUEST_FAILED = -32803;
    private static final int INVALID_PARAMS = -32602;

    private static final class Document {
        final String uri;
        final Path path;
        int version;
        TextLines lines;
        Analysis analysis;
        long analyzedGeneration = -1;
        /** The last version of this document that parsed, for the outline and completion fallback. */
        Module outline;
        TextLines outlineLines;

        Document(String uri, Path path, int version, String text) {
            this.uri = uri;
            this.path = path;
            this.version = version;
            this.lines = new TextLines(text);
        }
    }

    private static final class RequestFailure extends RuntimeException {
        final int code;

        RequestFailure(int code, String message) {
            super(message);
            this.code = code;
        }
    }

    private final Transport transport;
    private final Analyzer analyzer;
    private final Map<Path, Document> documents = new LinkedHashMap<>();
    private final Set<Path> pending = new LinkedHashSet<>();
    private long generation;
    private long diagnosticsDue = Long.MAX_VALUE;
    private boolean initialized;
    private boolean shutdown;
    private boolean prepareRename;
    private Integer exitCode;

    private LanguageServer(Transport transport, List<String> classpath) {
        this.transport = transport;
        this.analyzer = new Analyzer(classpath);
    }

    /** Entry point for {@code sprig lsp [--stdio] [--classpath PATH]...}. */
    public static int run(String[] args) {
        List<String> classpath = new ArrayList<>();
        for (int i = 1; i < args.length; i++) {
            if (args[i].equals("--stdio")) {
                continue;
            }
            if (args[i].equals("--classpath") && i + 1 < args.length) {
                classpath.add(args[++i]);
                continue;
            }
            System.err.println("sprig lsp: unexpected argument '" + args[i] + "'");
            System.err.println("Usage: sprig lsp [--stdio] [--classpath JAR_OR_DIR]...");
            return 2;
        }
        // Standard output carries the protocol; anything else printed goes to stderr.
        PrintStream protocol = System.out;
        System.setOut(System.err);
        LanguageServer server = new LanguageServer(
                new Transport(new BufferedInputStream(System.in), protocol), classpath);
        try {
            return server.serve();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return 1;
        }
    }

    private int serve() throws InterruptedException {
        BlockingQueue<Object> queue = new LinkedBlockingQueue<>();
        Thread reader = new Thread(() -> {
            try {
                for (String body = transport.read(); body != null; body = transport.read()) {
                    queue.put(body);
                }
            } catch (IOException e) {
                System.err.println("sprig lsp: cannot read from the client: " + e.getMessage());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            queue.add(END);
        }, "sprig-lsp-reader");
        reader.setDaemon(true);
        reader.start();
        while (exitCode == null) {
            Object next;
            if (pending.isEmpty()) {
                next = queue.take();
            } else {
                long wait = diagnosticsDue - System.currentTimeMillis();
                next = wait > 0 ? queue.poll(wait, TimeUnit.MILLISECONDS) : queue.poll();
                if (next == null) {
                    flushDiagnostics();
                    continue;
                }
            }
            if (next == END) {
                return shutdown ? 0 : 1;
            }
            dispatch((String) next);
        }
        return exitCode;
    }

    // ------------------------------------------------------------------
    // JSON-RPC
    // ------------------------------------------------------------------

    private void dispatch(String body) {
        Map<String, Object> message;
        try {
            message = Json.object(Json.parse(body));
        } catch (Json.ParseException e) {
            sendError(null, -32700, "Parse error: " + e.getMessage());
            return;
        }
        if (message == null) {
            sendError(null, -32600, "A message must be a JSON object");
            return;
        }
        String method = Json.string(message, "method");
        if (method == null) {
            return; // A response; this server sends no requests.
        }
        boolean request = message.containsKey("id");
        Object id = message.get("id");
        Map<String, Object> params = Json.object(message, "params");
        try {
            Object result = handle(method, params, request);
            if (request) {
                sendResult(id, result == NO_RESPONSE ? null : result);
            }
        } catch (RequestFailure failure) {
            if (request) {
                sendError(id, failure.code, failure.getMessage());
            }
        } catch (RuntimeException | LinkageError | StackOverflowError e) {
            System.err.println("sprig lsp: internal error while handling " + method);
            e.printStackTrace(System.err);
            if (request) {
                sendError(id, -32603, "Internal error: " + e);
            }
        }
    }

    private Object handle(String method, Map<String, Object> params, boolean request) {
        if (method.equals("exit")) {
            exitCode = shutdown ? 0 : 1;
            return NO_RESPONSE;
        }
        if (!initialized && !method.equals("initialize")) {
            if (request) {
                throw new RequestFailure(-32002, "The server is not initialized");
            }
            return NO_RESPONSE;
        }
        if (shutdown && request) {
            throw new RequestFailure(-32600, "The server is shutting down");
        }
        switch (method) {
            case "initialize":
                return initialize(params);
            case "shutdown":
                shutdown = true;
                return null;
            case "textDocument/didOpen":
                didOpen(params);
                return NO_RESPONSE;
            case "textDocument/didChange":
                didChange(params);
                return NO_RESPONSE;
            case "textDocument/didClose":
                didClose(params);
                return NO_RESPONSE;
            case "textDocument/didSave":
                generation++;
                for (Path path : documents.keySet()) {
                    schedule(path, 0);
                }
                return NO_RESPONSE;
            case "textDocument/hover":
                return hover(params);
            case "textDocument/definition":
                return definition(params);
            case "textDocument/references":
                return references(params);
            case "textDocument/documentSymbol":
                return documentSymbols(params);
            case "textDocument/completion":
                return completion(params);
            case "textDocument/formatting":
                return formatting(params);
            case "textDocument/prepareRename":
                return prepareRename(params);
            case "textDocument/rename":
                return rename(params);
            default:
                if (request) {
                    throw new RequestFailure(-32601, "Unsupported method: " + method);
                }
                return NO_RESPONSE;
        }
    }

    private void sendResult(Object id, Object result) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("result", result);
        send(message);
    }

    private void sendError(Object id, int code, String text) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("code", code);
        error.put("message", text);
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("id", id);
        message.put("error", error);
        send(message);
    }

    private void notify(String method, Object params) {
        Map<String, Object> message = new LinkedHashMap<>();
        message.put("jsonrpc", "2.0");
        message.put("method", method);
        message.put("params", params);
        send(message);
    }

    private void send(Map<String, Object> message) {
        try {
            transport.write(ToolJson.encode(message));
        } catch (IOException e) {
            System.err.println("sprig lsp: cannot write to the client: " + e.getMessage());
            exitCode = 1;
        }
    }

    // ------------------------------------------------------------------
    // Lifecycle and document synchronization
    // ------------------------------------------------------------------

    private Map<String, Object> initialize(Map<String, Object> params) {
        initialized = true;
        Map<String, Object> rename = Json.object(Json.object(Json.object(params, "capabilities"), "textDocument"),
                "rename");
        prepareRename = Json.bool(rename, "prepareSupport");
        Map<String, Object> sync = new LinkedHashMap<>();
        sync.put("openClose", true);
        sync.put("change", 1);
        sync.put("save", Map.of("includeText", false));
        Map<String, Object> capabilities = new LinkedHashMap<>();
        capabilities.put("positionEncoding", "utf-16");
        capabilities.put("textDocumentSync", sync);
        capabilities.put("hoverProvider", true);
        capabilities.put("definitionProvider", true);
        capabilities.put("referencesProvider", true);
        capabilities.put("documentSymbolProvider", true);
        capabilities.put("completionProvider", Map.of("triggerCharacters", List.of("."), "resolveProvider", false));
        capabilities.put("documentFormattingProvider", true);
        capabilities.put("renameProvider", prepareRename ? Map.of("prepareProvider", true) : true);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capabilities", capabilities);
        result.put("serverInfo", Map.of("name", "sprig", "version", Catalog.COMPILER_VERSION));
        return result;
    }

    private void didOpen(Map<String, Object> params) {
        Map<String, Object> item = Json.object(params, "textDocument");
        String uri = Json.string(item, "uri");
        String text = Json.string(item, "text");
        Path path = pathOf(uri);
        if (path == null || text == null) {
            return;
        }
        documents.put(path, new Document(uri, path, Json.integer(item, "version", 0), text));
        generation++;
        schedule(path, 0);
        scheduleDependents(path, 0);
    }

    private void didChange(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return;
        }
        String text = document.lines.text;
        List<Object> changes = Json.array(params.get("contentChanges"));
        for (Object value : changes == null ? List.of() : changes) {
            Map<String, Object> change = Json.object(value);
            String replacement = Json.string(change, "text");
            if (replacement == null) {
                continue;
            }
            Map<String, Object> range = Json.object(change, "range");
            if (range == null) {
                text = replacement;
            } else {
                TextLines lines = new TextLines(text);
                int from = offset(lines, Json.object(range, "start"));
                int to = offset(lines, Json.object(range, "end"));
                text = text.substring(0, Math.min(from, to)) + replacement + text.substring(Math.max(from, to));
            }
        }
        document.version = Json.integer(Json.object(params, "textDocument"), "version", document.version + 1);
        document.lines = new TextLines(text);
        generation++;
        schedule(document.path, CHANGE_DELAY_MS);
        scheduleDependents(document.path, CHANGE_DELAY_MS);
    }

    private void didClose(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return;
        }
        documents.remove(document.path);
        pending.remove(document.path);
        generation++;
        notify("textDocument/publishDiagnostics", Map.of("uri", document.uri, "diagnostics", List.of()));
        scheduleDependents(document.path, 0);
    }

    private void schedule(Path path, long delay) {
        pending.add(path);
        diagnosticsDue = System.currentTimeMillis() + delay;
    }

    /** Open documents whose last check read this file need a new check. */
    private void scheduleDependents(Path changed, long delay) {
        for (Document other : documents.values()) {
            if (other.path.equals(changed)) {
                continue;
            }
            if (other.analysis == null || other.analysis.compilation == null
                    || other.analysis.compilation.modules.stream().anyMatch(m -> m.path.equals(changed))) {
                schedule(other.path, delay);
            }
        }
    }

    private void flushDiagnostics() {
        List<Path> due = new ArrayList<>(pending);
        pending.clear();
        diagnosticsDue = Long.MAX_VALUE;
        for (Path path : due) {
            Document document = documents.get(path);
            if (document == null) {
                continue;
            }
            try {
                analysis(document);
            } catch (RuntimeException | LinkageError | StackOverflowError e) {
                // One file the compiler cannot handle must not stop the server.
                System.err.println("sprig lsp: internal error while checking " + path);
                e.printStackTrace(System.err);
            }
        }
    }

    /** The analysis of the document's current text, publishing its diagnostics when it is new. */
    private Analysis analysis(Document document) {
        if (document.analysis != null && document.analyzedGeneration == generation) {
            return document.analysis;
        }
        Analysis analysis = analyzer.analyze(document.path, overlays());
        document.analysis = analysis;
        document.analyzedGeneration = generation;
        Module module = analysis.module(document.path);
        if (module != null && parsed(analysis, document)) {
            document.outline = module;
            document.outlineLines = document.lines;
        }
        pending.remove(document.path);
        publish(document, analysis);
        return analysis;
    }

    private boolean parsed(Analysis analysis, Document document) {
        for (Diagnostic diagnostic : analysis.diagnostics) {
            if ((diagnostic.phase == Phase.LEX || diagnostic.phase == Phase.SYNTAX)
                    && document.path.equals(pathOf(diagnostic.uri))) {
                return false;
            }
        }
        return true;
    }

    private Map<Path, String> overlays() {
        Map<Path, String> overlays = new HashMap<>();
        for (Document document : documents.values()) {
            overlays.put(document.path, document.lines.text);
        }
        return overlays;
    }

    private Map<Path, String> overlaysWith(Path path, String text) {
        Map<Path, String> overlays = overlays();
        overlays.put(path, text);
        return overlays;
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    private void publish(Document document, Analysis analysis) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("uri", document.uri);
        params.put("version", document.version);
        params.put("diagnostics", diagnostics(document, analysis));
        notify("textDocument/publishDiagnostics", params);
    }

    private List<Map<String, Object>> diagnostics(Document document, Analysis analysis) {
        List<Map<String, Object>> out = new ArrayList<>();
        Module main = analysis.module(document.path);
        TextLines lines = analysis.text(document.path);
        for (Diagnostic diagnostic : analysis.diagnostics) {
            Path path = pathOf(diagnostic.uri);
            String message = message(diagnostic);
            List<Map<String, Object>> related = new ArrayList<>();
            Map<String, Object> range;
            if (document.path.equals(path)) {
                range = lines.range(diagnostic.span);
                for (Diagnostic.Related item : diagnostic.related) {
                    if (item.span != null) {
                        related.add(related(document.uri, lines.range(item.span), item.message));
                    }
                }
            } else {
                // Reported elsewhere (an imported module or the manifest): show it on
                // the import that leads there, or on the first line.
                range = lines.range(importLeadingTo(main, path));
                if (path != null) {
                    message = path.getFileName() + (diagnostic.span == null ? "" : ":" + diagnostic.span.display())
                            + ": " + message;
                    related.add(related(uriOf(path), analysis.text(path).range(diagnostic.span), diagnostic.message));
                }
            }
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("range", range);
            item.put("severity", diagnostic.isError() ? 1 : 2);
            item.put("code", diagnostic.code);
            item.put("source", "sprig");
            item.put("message", message);
            if (!related.isEmpty()) {
                item.put("relatedInformation", related);
            }
            if (diagnostic.relatedHelp != null) {
                // The `sprig help` topic, as `relatedHelp` in `sprig check --json`.
                item.put("data", Map.of("relatedHelp", diagnostic.relatedHelp));
            }
            out.add(item);
        }
        return out;
    }

    private static String message(Diagnostic diagnostic) {
        StringBuilder out = new StringBuilder(diagnostic.message);
        if (diagnostic.expectedType != null || diagnostic.actualType != null) {
            out.append(" (expected ").append(diagnostic.expectedType == null ? "?" : diagnostic.expectedType)
                    .append(", actual ").append(diagnostic.actualType == null ? "?" : diagnostic.actualType)
                    .append(')');
        }
        if (diagnostic.hint != null) {
            out.append("\nhint: ").append(diagnostic.hint);
        }
        return out.toString();
    }

    private static Map<String, Object> related(String uri, Map<String, Object> range, String message) {
        Map<String, Object> location = new LinkedHashMap<>();
        location.put("uri", uri);
        location.put("range", range);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("location", location);
        out.put("message", message);
        return out;
    }

    private static Span importLeadingTo(Module main, Path target) {
        if (main == null || target == null) {
            return null;
        }
        for (Decl.Import imp : main.imports) {
            String alias = ImportNames.aliasFor(imp);
            Module imported = alias == null ? null : main.importedModules.get(alias);
            if (imported != null && reaches(imported, target, new HashSet<>())) {
                return imp.span;
            }
        }
        return null;
    }

    private static boolean reaches(Module module, Path target, Set<Module> seen) {
        if (module.path.equals(target)) {
            return true;
        }
        if (!seen.add(module)) {
            return false;
        }
        for (Module dependency : module.importedModules.values()) {
            if (reaches(dependency, target, seen)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Language features
    // ------------------------------------------------------------------

    private Map<String, Object> hover(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        Analysis analysis = analysis(document);
        Occurrence occurrence = occurrenceAt(document, analysis, params);
        if (occurrence == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("contents", Map.of("kind", "markdown", "value", Describe.hover(analysis, occurrence)));
        result.put("range", analysis.text(document.path).range(occurrence.span));
        return result;
    }

    private Map<String, Object> definition(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        Analysis analysis = analysis(document);
        Occurrence occurrence = occurrenceAt(document, analysis, params);
        if (occurrence == null || occurrence.target == null) {
            return null;
        }
        Target target = occurrence.target;
        if (target.kind == SymbolIndex.Kind.MODULE) {
            return location(target.path, null, analysis);
        }
        return target.nameSpan == null ? null : location(target.path, target.nameSpan, analysis);
    }

    private List<Map<String, Object>> references(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        Analysis analysis = analysis(document);
        Occurrence occurrence = occurrenceAt(document, analysis, params);
        if (occurrence == null || occurrence.target == null) {
            return List.of();
        }
        Target target = occurrence.target;
        boolean includeDeclaration = Json.bool(Json.object(params, "context"), "includeDeclaration");
        List<Analysis> analyses = new ArrayList<>();
        analyses.add(analysis);
        if (!target.local()) {
            analyses.addAll(otherAnalyses(document, target.name));
        }
        Map<String, Map<String, Object>> locations = new LinkedHashMap<>();
        for (Analysis other : analyses) {
            for (Occurrence found : other.index().occurrencesOf(target.key())) {
                if (found.declaration && !includeDeclaration) {
                    continue;
                }
                String key = found.path + ":" + found.span.startLine + ":" + found.span.startColumn;
                locations.putIfAbsent(key, location(found.path, found.span, other));
            }
        }
        return new ArrayList<>(locations.values());
    }

    /**
     * Other places that can refer to a non-local declaration: open documents,
     * and the files of the same project that mention the name.
     */
    private List<Analysis> otherAnalyses(Document document, String name) {
        List<Analysis> out = new ArrayList<>();
        Set<Path> seen = new HashSet<>();
        seen.add(document.path);
        for (Document other : new ArrayList<>(documents.values())) {
            if (seen.add(other.path)) {
                out.add(analysis(other));
            }
        }
        Path manifest = Project.findManifest(document.path.getParent());
        if (manifest == null) {
            return out;
        }
        Pattern word = Pattern.compile("\\b" + Pattern.quote(name) + "\\b");
        Map<Path, String> overlays = overlays();
        for (Path file : projectSources(manifest.getParent())) {
            if (out.size() >= MAX_REFERENCE_FILES) {
                break;
            }
            if (!seen.add(file) || !manifest.equals(Project.findManifest(file.getParent()))) {
                continue;
            }
            try {
                if (!word.matcher(Files.readString(file, StandardCharsets.UTF_8)).find()) {
                    continue;
                }
            } catch (IOException | RuntimeException e) {
                continue;
            }
            out.add(analyzer.analyze(file, overlays));
        }
        return out;
    }

    private static List<Path> projectSources(Path root) {
        List<Path> out = new ArrayList<>();
        try {
            Files.walkFileTree(root, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attributes) {
                    String name = dir.getFileName() == null ? "" : dir.getFileName().toString();
                    if (!dir.equals(root) && (name.startsWith(".") || SKIPPED_DIRECTORIES.contains(name))) {
                        return FileVisitResult.SKIP_SUBTREE;
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attributes) {
                    if (file.toString().endsWith(".spr")) {
                        out.add(file.toAbsolutePath().normalize());
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFileFailed(Path file, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            return out;
        }
        out.sort(Comparator.naturalOrder());
        return out;
    }

    private List<Map<String, Object>> documentSymbols(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        analysis(document);
        return document.outline == null ? List.of() : Outline.symbols(document.outline, document.outlineLines);
    }

    private Map<String, Object> completion(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        Map<String, Object> position = Json.object(params, "position");
        int line = Json.integer(position, "line", 0);
        int character = Json.integer(position, "character", 0);
        List<Map<String, Object>> items = Completion.complete(document.path, document.lines, line, character,
                document.outline, text -> analyzer.analyze(document.path, overlaysWith(document.path, text)));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("isIncomplete", false);
        result.put("items", items);
        return result;
    }

    private List<Map<String, Object>> formatting(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        // Editors on Windows usually keep CRLF documents, while `sprig fmt`
        // writes LF: compare in LF and answer in the document's line endings.
        String text = document.lines.text;
        boolean crlf = text.contains("\r\n");
        String lf = crlf ? text.replace("\r\n", "\n") : text;
        String formatted;
        try {
            formatted = SourceFormatter.format(document.path, lf, new Diagnostics());
        } catch (RuntimeException e) {
            formatted = null;
        }
        if (formatted == null) {
            return null;
        }
        if (formatted.equals(lf)) {
            return List.of();
        }
        Map<String, Object> edit = new LinkedHashMap<>();
        edit.put("range", document.lines.fullRange());
        edit.put("newText", crlf ? formatted.replace("\n", "\r\n") : formatted);
        return List.of(edit);
    }

    private Map<String, Object> prepareRename(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        Analysis analysis = analysis(document);
        Occurrence occurrence = renameable(document, analysis, params);
        if (occurrence == null) {
            return null;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("range", analysis.text(document.path).range(occurrence.span));
        result.put("placeholder", occurrence.target.name);
        return result;
    }

    private Map<String, Object> rename(Map<String, Object> params) {
        Document document = document(params);
        if (document == null) {
            return null;
        }
        String newName = Json.string(params, "newName");
        if (newName == null || !IDENTIFIER.matcher(newName).matches() || Completion.KEYWORDS.contains(newName)
                || newName.equals(Completion.PLACEHOLDER)) {
            throw new RequestFailure(INVALID_PARAMS, "'" + newName + "' is not a valid Sprig name.");
        }
        Analysis before = analysis(document);
        Occurrence occurrence = renameable(document, before, params);
        if (occurrence == null) {
            return null;
        }
        Target target = occurrence.target;
        List<Occurrence> sites = new ArrayList<>(before.index().occurrencesOf(target.key()));
        sites.removeIf(site -> !site.path.equals(document.path));
        sites.sort(Comparator.<Occurrence>comparingInt(site -> site.span.startLine)
                .thenComparingInt(site -> site.span.startColumn));
        Map<String, Object> changes = new LinkedHashMap<>();
        if (newName.equals(target.name)) {
            return Map.of("changes", changes);
        }
        verifyRename(document, before, target, sites, newName);
        TextLines lines = before.text(document.path);
        List<Map<String, Object>> edits = new ArrayList<>();
        for (Occurrence site : sites) {
            Map<String, Object> edit = new LinkedHashMap<>();
            edit.put("range", lines.range(site.span));
            edit.put("newText", newName);
            edits.add(edit);
        }
        changes.put(document.uri, edits);
        return Map.of("changes", changes);
    }

    private Occurrence renameable(Document document, Analysis analysis, Map<String, Object> params) {
        Occurrence occurrence = occurrenceAt(document, analysis, params);
        if (occurrence == null || occurrence.target == null) {
            return null;
        }
        if (!occurrence.target.local()) {
            throw new RequestFailure(REQUEST_FAILED, "Only local variables and parameters can be renamed; '"
                    + occurrence.target.name + "' can be used from other files.");
        }
        if (!analysis.resolved()) {
            throw new RequestFailure(REQUEST_FAILED,
                    "This file has errors that stop name resolution; fix them before renaming.");
        }
        return occurrence;
    }

    /**
     * Checks the renamed text before answering: the same uses must refer to
     * the renamed declaration, nothing else may start referring to it, and no
     * new error may appear.
     */
    private void verifyRename(Document document, Analysis before, Target target, List<Occurrence> sites,
                              String newName) {
        TextLines lines = before.text(document.path);
        StringBuilder text = new StringBuilder(lines.text);
        for (int i = sites.size() - 1; i >= 0; i--) {
            Span span = sites.get(i).span;
            int from = lines.offset(span.startLine, lines.utf16(span.startLine, span.startColumn));
            text.replace(from, from + target.name.length(), newName);
        }
        int delta = newName.length() - target.name.length();
        Set<String> expected = new HashSet<>();
        int[] declaration = null;
        Map<Integer, Integer> shifts = new HashMap<>();
        for (Occurrence site : sites) {
            int line = site.span.startLine;
            int shift = shifts.getOrDefault(line, 0);
            int column = site.span.startColumn + shift;
            expected.add(line + ":" + column);
            if (site.declaration) {
                declaration = new int[] {line, column};
            }
            shifts.put(line, shift + delta);
        }
        String refusal = "Renaming '" + target.name + "' to '" + newName + "' would change what other names refer to.";
        if (declaration == null) {
            throw new RequestFailure(REQUEST_FAILED, refusal);
        }
        Analysis after = analyzer.analyze(document.path, overlaysWith(document.path, text.toString()));
        if (!after.resolved()) {
            throw new RequestFailure(REQUEST_FAILED, newError(before, after, newName));
        }
        Occurrence renamed = after.index().at(document.path, declaration[0], declaration[1]);
        if (renamed == null || renamed.target == null || !renamed.target.name.equals(newName)) {
            throw new RequestFailure(REQUEST_FAILED, refusal);
        }
        Set<String> actual = new HashSet<>();
        for (Occurrence found : after.index().occurrencesOf(renamed.target.key())) {
            actual.add(found.path.equals(document.path)
                    ? found.span.startLine + ":" + found.span.startColumn : found.path.toString());
        }
        if (!actual.equals(expected)) {
            throw new RequestFailure(REQUEST_FAILED, refusal);
        }
        if (errors(after) > errors(before)) {
            throw new RequestFailure(REQUEST_FAILED, newError(before, after, newName));
        }
    }

    private static int errors(Analysis analysis) {
        int count = 0;
        for (Diagnostic diagnostic : analysis.diagnostics) {
            if (diagnostic.isError()) {
                count++;
            }
        }
        return count;
    }

    private static String newError(Analysis before, Analysis after, String newName) {
        Set<String> known = new HashSet<>();
        for (Diagnostic diagnostic : before.diagnostics) {
            known.add(diagnostic.code + " " + diagnostic.message);
        }
        for (Diagnostic diagnostic : after.diagnostics) {
            if (diagnostic.isError() && !known.contains(diagnostic.code + " " + diagnostic.message)) {
                return "Renaming to '" + newName + "' would cause " + diagnostic.code + ": " + diagnostic.message;
            }
        }
        return "Renaming to '" + newName + "' would introduce an error.";
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private Document document(Map<String, Object> params) {
        Path path = pathOf(Json.string(Json.object(params, "textDocument"), "uri"));
        return path == null ? null : documents.get(path);
    }

    private static Occurrence occurrenceAt(Document document, Analysis analysis, Map<String, Object> params) {
        Map<String, Object> position = Json.object(params, "position");
        int line = Json.integer(position, "line", 0);
        int character = Json.integer(position, "character", 0);
        int column = analysis.text(document.path).codePoints(line, character);
        return analysis.index().at(document.path, line, column);
    }

    private Map<String, Object> location(Path path, Span span, Analysis analysis) {
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("uri", uriOf(path));
        out.put("range", analysis.text(path).range(span));
        return out;
    }

    private String uriOf(Path path) {
        Document document = documents.get(path);
        return document != null ? document.uri : path.toUri().toString();
    }

    private static int offset(TextLines lines, Map<String, Object> position) {
        return lines.offset(Json.integer(position, "line", 0), Json.integer(position, "character", 0));
    }

    static Path pathOf(String uri) {
        if (uri == null) {
            return null;
        }
        try {
            URI parsed = URI.create(uri);
            if (!"file".equalsIgnoreCase(parsed.getScheme())) {
                return null;
            }
            return Path.of(parsed).toAbsolutePath().normalize();
        } catch (IllegalArgumentException | FileSystemNotFoundException e) {
            return null;
        }
    }
}
