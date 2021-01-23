# Apex Legends: Statistics Parser

This program allows a user to query the Apex Legends API to obtain
player statistics via the player's EA Origin UID.

## Usage

Using the compiled binary for macOS or Linux:

```
$ ./apex-stats $UID
```

Using Java:

```
$ java -cp target/apex-stats.jar clojure.main -m apex-stats.apex $UID
```

Using Clojure:

```
$ clojure -M:apex-stats/core $UID
```

An example of what you will get as a return value:

```
{:frame "Mapped Out",
 :uid 2300148116,
 :name "niamu_com",
 :rank {:points 1220, :name "Silver IV"},
 :legend "Mirage",
 :seconds-since-server-change -1,
 :account {:progress "23%", :level 209},
 :trackers
 [{"Season 6 Kills" 0} {"Damage Done" 40736} {"Season 5 Wins" 0}],
 :status
 {:online? false,
  :in-match? false,
  :party {:in-match? false, :full? false},
  :joinable? false},
 :badges
 [{"Account Level" 209} {"Apex Mirage" 2} {"Rapid Elimination" 1}],
 :pose "Baller",
 :intro "Gonna see a lot of me",
 :skin "The Revenger"}
```

In many cases you may see values that are numeric IDs and not a proper
readable string such as `:pose 808193896`. These are examples of data
that have not yet been recorded in the `resources` directory. This
form of data collection is a manual process at the moment and
contributions to fill in these gaps are greatly appreciated!

## License

Copyright © 2020 Brendon Walsh.

Licensed under the EPL (see the file LICENSE).
