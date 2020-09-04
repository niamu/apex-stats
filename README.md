# Apex Legends: Statistics Parser

This program allows a user to query EA's Origin user database and
obtain a user ID which can then be used against the Apex Legends API
to obtain player statistics.

## Usage

The following environment variables need to be provided in order to
authenticate with Origin and query for users by username:

- `ORIGIN_EMAIL`
- `ORIGIN_PASSWORD`
- `ORIGIN_TOTP_SECRET`: The TOTP secret used to generate your 6 digit
  authenticator code. (The text represented by the QR codes when
  setting up TOTP.)

With those environment variables set the program can be run to query
for the Apex Legends statistics for a specified username:

```
$ ORIGIN_EMAIL="test@example.com" \
    ORIGIN_PASSWORD="p@55W0rD" \
    ORIGIN_TOTP_SECRET="t0TpS3cREt123456" \
    clojure -A:apex $USERNAME
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
