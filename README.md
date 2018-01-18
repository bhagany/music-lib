# Music library - Etsy assignment

This was a lot of fun! It was a great excuse to try out a few things I've been looking for
opportunities to try out. While I tried to avoid it, I chose to use Clojure - the problem just
cried out for a context-free grammar parser, and all of my exposure to CFG's is in the context of
Clojure. I also realized that `clojure.spec`, the generative testing library that I talked to
Rebecca and Jason about, can be used as a parser generator for CFG's, so it just seemed too
right to ignore. I realize that lisps take a bit of getting used to for people who aren't familiar
with them, so I left a lot of comments to explain what's going on. Hopefully that helps a bit.

I should mention that I finished the primary assignment and the unit tests in an evening, but
decided to put some extra time in for a demonstration of using `clojure.spec` for generative
testing. It's a nicely isolated piece of the project, so it should be easy to exclude for the
purposes of evaluation - I just really wanted to show you what I was talking about in my
previous interviews, and this seemed like the perfect opportunity.

Running the project has a few dependencies that need to be installed manually: Java, and a build
tool for Clojure called boot. If you're on a Mac, installation is easy with Homebrew. If you
don't have a recent JDK installed:

```brew cask install java```

Then, to install boot:

```brew install boot-clj```

If you're not on a Mac, you can find instructions here: https://github.com/boot-clj/boot#install

Once boot is installed, you can run the project with `boot run` in the project directory, and run
the unit tests with `boot test`. The first one of these commands you run will take a while to
start, because it will need to download the project dependencies. These should include
Clojure itself, and two libraries I used for testing (plus their dependencies). Subsequent
runs will be faster.

For the extra generative testing demo, I wrote a few small commands so that you can get a feel
for it. `boot gen-test` will generate and run 20 random tests (by default). You can change the
number of tests by doing `boot gen-test -n <num>`. Note: you may run into a bug that I didn't
have time to fix - when a generated artist has multiple tracks with listens that add up to
something greater than Java's maximum integer, you'll get an overflow when running the "top
artists" command. I found this bug using generative testing :). You might find others this way
too!

Then, as a demonstration of generating random data, you can do `boot gen-store` to see a
random music store (do it multiple times to get an idea of the variation that gets produced).
Similarly `boot gen-input` will generate random valid input strings. You can do `boot gen-input
-n <num>` to control the number of inputs. The higher you set the number, the crazier the
inputs get.

The main part of the code is in src/music_lib/core.clj, unit tests are in
test/music_lib/core_test.clj, and the extra generative testing stuff can be found in
test/music_lib/spec_test.clj.

With that, here are the answers to the questions in the assignment:

1. How have you gained confidence in your code?
  I think of code confidence in three levels. First, I like to develop interactively with a REPL,
  so I have a tight feedback loop that gives me confidence that the specific code I'm working on
  is doing what I expect. Second, unit tests give me confidence that I probably haven't broken
  anything in the broader codebase that depends on the things I've changed. Third, generative
  tests give me some confidence that many inputs that nobody would have thought of are covered
  correctly by the code that they test.

2. One of the things we'll be evaluating is how your code is organized. Why did you choose the structure that you did? What principles were important to you as you organized this code?
  Clojure only makes a single compilation pass, which means that organization within a single
  file is constrained by the relative dependencies of the funtions in that file. Practically,
  this means that lower-level code has to be at the top of the file, while the higher level code
  that depends on it is further down. In languages that do multiple passes (like PHP), I would
  prefer to put higher-level code near the top, and implementation details further down.

  I considered breaking core.clj up into multiple files, but it was concise enough that this
  seemed like overkill. For what it's worth though, if it became necessary, I would split it up
  into one file for the parser, one file for command parsing (add, list, listen to, etc), and then
  a small core namespace that had the high-level API (like `expr` and `handle-input`), as well
  as the main control loop. If the mini-language grew to contain a lot of commands, I would
  consider using a file for each command instead of putting them all in the same file.

3. What are the performance characteristics of your implementation? Does it perform some operations faster than others? Explain any tradeoffs you made in architecting your solution.
  I love that you asked this question - the answer is not what you'd probably expect, because of
  Clojure. Clojure's datastructures are immutable, and they have slightly different performance
  characteristics than similar datastructures in other languages, but usually this difference
  can be ignored. For instance, reads and writes to Clojure's map (which are used to implement
  the music store in this exercise) are technically O(log(n)). However, the "log" there is log_32,
  which grows really, really slowly. Clojure programmers pretend that these operations are O(1)
  like in most programming languages, because people can't perceive the difference, even for large
  n.

  So, the short answer to the first question is: parsing is O(n) in input size, and simple reads
  and writes to the store are logarithmic (that is, log_32) in the number of cumulative inputs in
  the store. The more complex operations like `list top 10 artists|tracks` are dominated by the
  sorting requirement, and so are O(n log(n)) in the number of artists or tracks in the store.

  It would be possible to avoid sorting (the most expensive operation here) by using a max-heap
  to keep track of listens. The insertion and modification cost of O(log(n)) would be paid on write,
  and reading the top n entries of the heap at read time would be O(n). The tradeoffs would include
  increased memory requirements, and increased code complexity. I would be very hesitant to make
  the code more complex in this way, without clear evidence of a real-world performance problem.
