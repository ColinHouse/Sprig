import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.SplittableRandom;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import sprig.runtime.StringOps;

/**
 * Differential test of sprig.runtime.StringOps under concurrency. Virtual
 * threads index a shared pool of short and long strings (Latin-1, BMP outside
 * Latin-1, surrogate pairs, combining marks, unpaired surrogates, lengths on
 * both sides of the count cache's threshold, equal texts in distinct objects)
 * with random operations and indexes, in and out of range. Every answer is
 * compared with an oracle built from String.codePoints(): values, elements,
 * slices, search positions, iteration, and the exact exception message.
 */
public final class StringOpsProbe {
    private record Sample(String text, int[] codes, int[] offsets) {
        static Sample of(String text) {
            int[] codes = text.codePoints().toArray();
            int[] offsets = new int[codes.length + 1];
            for (int i = 0; i < codes.length; i++) {
                offsets[i + 1] = offsets[i] + Character.charCount(codes[i]);
            }
            return new Sample(text, codes, offsets);
        }
    }

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 16;
        int steps = args.length > 1 ? Integer.parseInt(args[1]) : 60_000;
        List<Sample> pool = pool();
        CountDownLatch start = new CountDownLatch(threads);
        List<Future<long[]>> results = new ArrayList<>();
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int t = 0; t < threads; t++) {
                long seed = 0x157L * 31 + t;
                results.add(executor.submit(() -> {
                    start.countDown();
                    start.await();
                    return run(pool, new SplittableRandom(seed), steps);
                }));
            }
        }
        long checks = 0;
        long failures = 0;
        for (Future<long[]> result : results) {
            checks += result.get()[0];
            failures += result.get()[1];
        }
        System.out.println("StringOps probe: " + threads + " threads, " + pool.size() + " strings, "
                + checks + " checks, " + failures + " failures");
        System.exit(failures == 0 ? 0 : 1);
    }

    private static List<Sample> pool() {
        String bmp = "東京の空 敏捷的狐狸 한글 ";
        String mixed = "A😀東é" + "é" + "한𠀀 🦊京ä";
        String emoji = "😀🦊🐶👍";
        List<String> texts = new ArrayList<>(List.of(
                "", "a", "é", "東", "😀", "é", "ab😀", "short ascii", "café über", bmp, mixed,
                "\uD83D", "\uDE00", "\uD83Dx", "x\uDE00",
                "x".repeat(31), "x".repeat(32), "東".repeat(31), "東".repeat(32), "東".repeat(33),
                emoji.repeat(3) + "😀😀😀a", emoji.repeat(4), emoji.repeat(4) + "a",
                "the quick brown fox jumps over the lazy dog ".repeat(3),
                "café naïve über straße ".repeat(5),
                bmp.repeat(12), mixed.repeat(10), emoji.repeat(30),
                "éäõ".repeat(20),
                "\uD83D" + "xy".repeat(30) + "\uDE00",
                "a".repeat(20) + "\uD83D" + "b".repeat(20),
                bmp.repeat(400), mixed.repeat(150)));
        List<Sample> pool = new ArrayList<>();
        for (String text : texts) {
            pool.add(Sample.of(text));
            // The same contents in another object: identity, not equality, keys the cache.
            pool.add(Sample.of(new String(text.toCharArray())));
        }
        return pool;
    }

    private static long[] run(List<Sample> pool, SplittableRandom random, int steps) {
        long checks = 0;
        long failures = 0;
        for (int step = 0; step < steps; step++) {
            Sample sample = pool.get(random.nextInt(pool.size()));
            String text = sample.text;
            int n = sample.codes.length;
            String failure;
            switch (random.nextInt(8)) {
                case 0 -> failure = same(StringOps.length(text), (long) n, "length");
                case 1 -> {
                    int index = random.nextInt(-2, n + 2);
                    failure = index >= 0 && index < n
                            ? attempt(() -> StringOps.codePointAt(text, index), (long) sample.codes[index], null)
                            : attempt(() -> StringOps.codePointAt(text, index), null, "index " + index + ", code point length " + n);
                }
                case 2 -> {
                    int index = random.nextInt(-2, n + 2);
                    failure = index >= 0 && index < n
                            ? attempt(() -> StringOps.elementAt(text, index), Character.toString(sample.codes[index]), null)
                            : attempt(() -> StringOps.elementAt(text, index), null, "index " + index + ", code point length " + n);
                }
                case 3 -> {
                    int begin = random.nextInt(-2, n + 3);
                    failure = begin >= 0 && begin <= n
                            ? attempt(() -> StringOps.substring(text, begin), text.substring(sample.offsets[begin]), null)
                            : attempt(() -> StringOps.substring(text, begin), null, "index " + begin + ", code point length " + n);
                }
                case 4 -> {
                    int begin = random.nextInt(-2, n + 3);
                    int end = random.nextInt(-2, n + 3);
                    failure = begin >= 0 && end <= n && begin <= end
                            ? attempt(() -> StringOps.substring(text, begin, end),
                                    text.substring(sample.offsets[begin], sample.offsets[end]), null)
                            : attempt(() -> StringOps.substring(text, begin, end), null,
                                    "begin " + begin + ", end " + end + ", code point length " + n);
                }
                case 5, 6 -> {
                    String needle = needle(sample, random);
                    boolean last = random.nextBoolean();
                    int found = last ? text.lastIndexOf(needle) : text.indexOf(needle);
                    long expected = found < 0 ? -1 : position(sample, found);
                    failure = same(last ? StringOps.lastIndexOf(text, needle) : StringOps.indexOf(text, needle),
                            expected, (last ? "lastIndexOf " : "indexOf ") + needle);
                }
                default -> {
                    List<String> elements = new ArrayList<>();
                    for (String element : StringOps.codePoints(text)) {
                        elements.add(element);
                    }
                    List<String> expected = Arrays.stream(sample.codes).mapToObj(Character::toString).toList();
                    failure = same(elements, expected, "iteration");
                }
            }
            checks++;
            if (failure != null) {
                failures++;
                if (failures <= 5) {
                    System.out.println("FAIL on " + describe(text) + ": " + failure);
                }
            }
        }
        return new long[] {checks, failures};
    }

    /** A slice of the sample at code-point boundaries, or text that does not occur. */
    private static String needle(Sample sample, SplittableRandom random) {
        int n = sample.codes.length;
        if (n == 0 || random.nextInt(5) == 0) {
            return random.nextBoolean() ? "" : "☃missing";
        }
        int begin = random.nextInt(n);
        int end = Math.min(n, begin + 1 + random.nextInt(3));
        return sample.text.substring(sample.offsets[begin], sample.offsets[end]);
    }

    /** The code-point index of a char offset where a match starts. */
    private static long position(Sample sample, int offset) {
        int index = Arrays.binarySearch(sample.offsets, offset);
        // A match can start inside a pair only for a needle that begins with an
        // unpaired low surrogate; String.codePointCount defines that position.
        return index >= 0 ? index : sample.text.codePointCount(0, offset);
    }

    private interface Action {
        Object run();
    }

    private static String attempt(Action action, Object expected, String expectedMessage) {
        try {
            Object actual = action.run();
            if (expectedMessage != null) {
                return "expected an error '" + expectedMessage + "', got " + actual;
            }
            return same(actual, expected, "value");
        } catch (StringIndexOutOfBoundsException error) {
            if (expectedMessage == null) {
                return "unexpected error " + error.getMessage() + ", expected " + expected;
            }
            return same(error.getMessage(), expectedMessage, "error message");
        } catch (RuntimeException error) {
            return "unexpected " + error;
        }
    }

    private static String same(Object actual, Object expected, String what) {
        return actual.equals(expected) ? null : what + ": got " + actual + ", expected " + expected;
    }

    private static String describe(String text) {
        return text.length() > 24 ? "a " + text.length() + "-char text starting " + text.substring(0, 12) : "'" + text + "'";
    }
}
