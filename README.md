# 5Words25Letters
Finding 5 words with 5 different letters each, using all but one letter from the english alphabet

For context watch this: https://www.youtube.com/watch?v=c33AZBnRHks

I was disappointed, that the fastest java based solution ranked on 10th place (as of 29th of Oct/22), so I wanted to give it a try.
(see https://docs.google.com/spreadsheets/d/11sUBkPSEhbGx2K8ah6WbGV62P8ii5l5vVeMpkzk17PI/edit#gid=0)

# Usage:
`java WordleFast7 WORDSFILE [ITERATIONS]`

WORDSFILE - is the path to `words_alpha.txt` used by Matt Parker in his original 31 day run.

ITERATIONS - is 1 by default. Increase this to 20 to see how fast Java will get, after the JIT had time to compile the code.

# My Results
On my machine the first iteration takes somewhere between 50ms and 90ms and speeds up to 18ms - 20ms after some iterations.

# Optimizations

Pruning:
* sort words in lists by the least frequent letter they contain
  * split every such list in 2^6-buckets by the combination of the 6 globally most frequent letters they contain and when later searching a word, check only buckets that have none of those 6 letters in common with the already selected words
* choose recursively a single word from every list (means set of buckets)
  * skip lists for letters already used
* abort recursive search, when needing to skip more than one such list (because only one letter may be omitted)
* after finding 4 words and having skipped one letter stop recursion and use a Set to find last word

Other optimizations:
* use int as representation of a word (only 26 bits representing used letters)
* use Map with Integer key collecting all anagrams of a word
* use int pointer into byte-array as representation of a String
* omit string creation completely (on reading file and when printing solutions)
* use int-arrays instead of Lists
* reading whole file in one byte-array

# Further improvements
The code is single threaded and to my surprise first attempts to speed it up by splitting work on multiple threads did not show satisfying results yet.
