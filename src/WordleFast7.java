import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Pruning:
 * - sort words in lists by the least frequent letter they contain
 *   - split every such list in 2^6-buckets by the combination of the 6 globally most frequent letters they contain and when later searching a word check only buckets that have none of those 6 letters in common with the already selected words
 * - choose recursively a single word from every list (means set of buckets)
 *   - skip lists for letters already used
 * - abort recursive search, when needing to skip more than one such list (because only one letter may be omitted)
 * - after finding 4 words and having skipped one letter stop recursion and use a Set to find last word
 *
 * Other optimizations:
 * - use int as representation of a word (only 26 bits representing used letters)
 * - use Map with Integer key collecting all anagrams of a word
 * - use int pointer into byte-array as representation of a String
 * - omit string creation completely (on reading file and when printing solutions)
 * - use int-arrays instead of Lists
 * - reading whole file in one byte-array
 */
public class WordleFast7 {

	private static final int subLetterCount = 6;
	private static final int subLetterBuckets = 1 << subLetterCount;
	private static final int bucketMask = subLetterBuckets - 1;

	private static final String letterOrder = "qxjzvwfkbghcympduntliroase";
	private static final int[] letterIndexMap = new int[26];
	private static final int[] bucketShift = new int[subLetterCount];

	private static final int[][] bucketsToLookAt;

	static {
		bucketsToLookAt = new int[subLetterBuckets][];
		for (int i = 0; i < subLetterBuckets; i++) {
			int[] my = bucketsToLookAt[i] = new int[1 << Integer.bitCount(i)];
			int index = 0;
			for (int j = 0; j < subLetterBuckets; j++) {
				if ((i & j) == j) {
					my[index++] = j;
				}
			}
		}
	}

	static {
		for (int i = 0; i < letterOrder.length(); i++) {
			letterIndexMap[i] = letterOrder.charAt(i) - 'a';
		}
		int[] lastIndices = Arrays.copyOfRange(letterIndexMap, 26 - subLetterCount, 26);
		Arrays.sort(lastIndices);
		for (int i = 0; i < subLetterCount; i++) {
			bucketShift[i] = lastIndices[i] - i;
		}
	}

	private static int runCount = 0;

	public static void main(String[] args) throws IOException {
		final var filename = args.length > 0 ? args[0] : "resources/words_alpha.txt";
		final var iterations = args.length > 1 ? Integer.parseInt(args[1]) : args.length > 0 ? 1 : 10;
		final long startTime = System.nanoTime();
		for (int i = 0; i < iterations; i++) {
			new WordleFast7().start(new File(filename));
		}
		System.out.println("Avg time: " + (System.nanoTime() - startTime) / runCount / 1_000_000 + "ms");
	}

	private final Map<Integer, int[]> wordsSingle;
	private byte[] buf; // raw contents of the file
	private final long startTime;
	private long lastTime;
	private int solutions;

	WordleFast7() {
		this.startTime = this.lastTime = System.nanoTime();
		this.wordsSingle = new HashMap<>();
	}

	private void start(File file) throws IOException {
		runCount++;

		final var lines = getLines(file);
		printTime("ParsingFile");

		final int[][][] words = prepareDataStructures(lines, wordsSingle);
		printTime("Preparation");

		findSolution(0, 0, 0, new int[5], 0, words);
		printTime("\n--> Solutions: " + solutions + ". TotalTime:");
		System.out.println();
	}

	private int[][][] prepareDataStructures(int[] lines, Map<Integer,int[]> wordsSingle) {
		final int[][][] words = new int[26][subLetterBuckets][52];
		final int[][] wordCounts = new int[26][subLetterBuckets];

		// Prepare Segmented WordLists
		for (final var wordInBuf : lines) {
			if (wordInBuf < 0) {
				break;
			}
			final int wordId = wordToId(wordInBuf);
			if (wordId > 0) {
				int[] anagrams = wordsSingle.get(wordId);
				if (anagrams == null) {
					anagrams = new int[]{wordInBuf};

					final int bucketId = getBucketId(wordId);
					for (int letterIndex = 0; letterIndex < 26; letterIndex++) {
						final int letter = letterIndexMap[letterIndex];
						if ((wordId & (1 << letter)) != 0) {
							words[letter][bucketId][wordCounts[letter][bucketId]++] = wordId;
							break;
						}
					}

				} else {
					anagrams = Arrays.copyOf(anagrams, anagrams.length + 1);
					anagrams[anagrams.length - 1] = wordInBuf;
				}
				wordsSingle.put(wordId, anagrams);
			}
		}

		// shorten Arrays
		for (int letter = 0; letter < 26; letter++) {
			int letterIndex = letterOrder.charAt(letter) - 'a';
			for (int bucketIndex = 0, n = subLetterBuckets; bucketIndex < n; bucketIndex++) {
				words[letterIndex][bucketIndex] = Arrays.copyOf(words[letterIndex][bucketIndex], wordCounts[letterIndex][bucketIndex]);
			}
		}
		return words;
	}

	private int getBucketId(int wordId) {
		int bucketId = 0;
		for (int i = 0; i < subLetterCount; i++) {
			bucketId |= wordId >>> bucketShift[i] & (1 << i);
		}
		return bucketId;
	}

	private void findSolution(int wholeWordMask, int wordCount, int letterIndex, int[] solutionsWords, int skippedOneLetter, int[][][] words) {
		final int listIndex = letterIndexMap[letterIndex];
		final int myLetterId = 1 << listIndex;
		final var letterStillUnused = (wholeWordMask & myLetterId) == 0;
		if (letterStillUnused) {
			int[][] subBuckets = words[listIndex];
			final int inverseBucketId = getBucketId(wholeWordMask) ^ bucketMask;
			final Map<Integer, int[]> wordsSingle = this.wordsSingle;
			for (final int bucketId : bucketsToLookAt[inverseBucketId]) {
				final int[] wordList = subBuckets[bucketId];
				for (final int wordId : wordList) {
					if ((wholeWordMask & wordId) == 0) {
						solutionsWords[wordCount] = wordId;
						if (wordCount == 3 && skippedOneLetter > 0) {
							final int missingLetters = (wholeWordMask | wordId | skippedOneLetter) ^ 0x3ffffff;
							if (wordsSingle.containsKey(missingLetters)) {
								solutionsWords[4] = missingLetters;
								final int mySolutions = printSolution(solutionsWords);
								solutions += mySolutions;
							}
						} else if (wordCount == 4) {
							solutions += printSolution(solutionsWords);
						} else {
							findSolution(wholeWordMask | wordId, wordCount + 1, letterIndex + 1, solutionsWords, skippedOneLetter, words);
						}
					}
				}
			}

			if (skippedOneLetter > 0) {
				// You may not skip more than one letter
				return;
			}
			skippedOneLetter = myLetterId;
		}

		if (letterIndex < 21) {
			findSolution(wholeWordMask, wordCount, letterIndex + 1, solutionsWords, skippedOneLetter, words);
		}
	}

	private static final byte[] out = new byte[31];

	static {
		Arrays.fill(out, (byte) ' ');
		out[30] = '\n';
	}

	private int printSolution(int[] solutionsWords) {
		int count = 0;
		for (final var word1 : wordsSingle.get(solutionsWords[0])) {
			System.arraycopy(buf, word1, out, 0, 5);
			for (final var word2 : wordsSingle.get(solutionsWords[1])) {
				System.arraycopy(buf, word2, out, 6, 5);
				for (final var word3 : wordsSingle.get(solutionsWords[2])) {
					System.arraycopy(buf, word3, out, 12, 5);
					for (final var word4 : wordsSingle.get(solutionsWords[3])) {
						System.arraycopy(buf, word4, out, 18, 5);
						for (final var word5 : wordsSingle.get(solutionsWords[4])) {
							System.arraycopy(buf, word5, out, 24, 5);
							System.out.write(out, 0, 31);
							count++;
						}
					}
				}
			}
		}
		return count;
	}

	private int wordToId(int wordInBuf) {
		final byte[] buf = this.buf;
		int id = 0;
		for (int i = 0; i < 5; i++) {
			byte c = buf[wordInBuf + i];
			id |= 1 << (c - 'a');
		}
		return Integer.bitCount(id) == 5 ? id : 0;
	}

	private int[] getLines(File file) throws IOException {
		final byte[] buf = this.buf = new byte[4_300_000];
		printTime("Buffer Allocation");
		final int len;
		try (final var in = new FileInputStream(file)) {
			len = in.read(buf);
		}
		printTime("Read File");

		final var result = new int[17_000];
		int index = 0;
		for (int i = 0; i < len; i++) {
			final var start = i;
			while (i < len && buf[i] != '\n') {
				i++;
			}
			int count = i - start;
			if (count == 6) {
				result[index++] = start;
			}
		}

		result[index] = -1; // mark end
		return result;
	}

	private void printTime(final String id) {
		long now = System.nanoTime();
		System.out.println(id + " " + (now - startTime) / 1_000_000 + "ms" + " (+" + (now - lastTime) / 100_000 / 10F + "ms)");
		lastTime = now;
	}
}
