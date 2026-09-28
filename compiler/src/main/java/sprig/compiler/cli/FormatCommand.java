package sprig.compiler.cli;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import sprig.compiler.diag.*;
import sprig.compiler.front.SourceFormatter;

/** Explicit-only source formatting. Validate all files before replacing any. */
final class FormatCommand {
    static int run(String[] args) throws IOException {
        boolean check = false, json = false;
        Path target = null;
        for (int i=1; i<args.length; i++) {
            if (args[i].equals("--check")) check=true;
            else if (args[i].equals("--json")) json=true;
            else if (!args[i].startsWith("-") && target == null) target=Path.of(args[i]).toAbsolutePath().normalize();
            else return error("Unexpected fmt argument: " + args[i], java.util.Arrays.asList(args).contains("--json"));
        }
        if (target == null) return error("Usage: sprig fmt <file.spr|directory> [--check] [--json]", json);
        List<Path> files;
        if (Files.isDirectory(target)) {
            Path sourceRoot = target;
            if (Files.isRegularFile(target.resolve("sprig.toml"))) {
                try { sourceRoot = target.resolve(sprig.compiler.project.Project.load(target.resolve("sprig.toml")).source); }
                catch (sprig.compiler.project.Toml.TomlException e) { return error(e.getMessage(), json); }
            } else if (Files.isDirectory(target.resolve("src"))) sourceRoot = target.resolve("src");
            if (!Files.isDirectory(sourceRoot)) return error("Source directory does not exist: " + sourceRoot, json);
            final Path selectedRoot = sourceRoot;
            try (var stream=Files.walk(sourceRoot)) {
                files=stream.filter(Files::isRegularFile).filter(p->p.toString().endsWith(".spr"))
                    .filter(p-> !excluded(selectedRoot.relativize(p))).sorted().toList();
            }
        } else if (Files.isRegularFile(target) && target.toString().endsWith(".spr")) files=List.of(target);
        else return error("Expected a Sprig source file or directory: " + target,json);
        Diagnostics diagnostics = new Diagnostics();
        List<Path> changed = new ArrayList<>();
        List<String> outputs = new ArrayList<>();
        for (Path file:files) {
            String source=Files.readString(file);
            Diagnostics local=new Diagnostics();
            String formatted=SourceFormatter.format(file,source,local);
            local.all().forEach(diagnostics::add);
            outputs.add(formatted);
            if (formatted!=null && !source.equals(formatted)) changed.add(file);
        }
        int exit=diagnostics.hasErrors() || (check && !changed.isEmpty()) ? 1 : 0;
        var changedSet = new java.util.HashSet<>(changed);
        if (!check && !diagnostics.hasErrors()) {
            for (int i=0;i<files.size();i++) if (changedSet.contains(files.get(i))) replace(files.get(i),outputs.get(i));
        }
        if (json) System.out.print(JsonWriter.result(diagnostics.all(),target.toUri().toString(),"fmt",exit,null,
            Map.of("checkedFiles",files.stream().map(Path::toString).toList(),"changedFiles",changed.stream().map(Path::toString).toList(),"checkOnly",check)));
        else {
            for (var diagnostic:diagnostics.all()) System.err.println(diagnostic.format());
            for (Path file:changed) System.out.println((check ? "Would format " : diagnostics.hasErrors() ? "Not written " : "Formatted ") + file);
        }
        return exit;
    }
    private static boolean excluded(Path relative) {
        for (Path part:relative) if (java.util.Set.of("build","bin","node_modules",".git",".sprig","dist").contains(part.toString())) return true;
        return false;
    }
    private static void replace(Path file,String content) throws IOException {
        if (Files.isSymbolicLink(file)) throw new IOException("Refusing to replace a symbolic link: " + file);
        Path temp=Files.createTempFile(file.getParent(),".sprig-fmt-",".tmp");
        try {
            Files.writeString(temp,content,StandardCharsets.UTF_8);
            if (Files.getFileStore(file).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(temp,Files.getPosixFilePermissions(file));
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING);
        } finally { Files.deleteIfExists(temp); }
    }
    private static int error(String message,boolean json) {
        if (json) System.out.print(JsonWriter.result(List.of(Diagnostic.error(Codes.CLI_OPTION,Phase.SYNTAX,message,null,null)),null,"fmt",2,null));
        else System.err.println(message);
        return 2;
    }
}
