# Changelog

## 1.1.0

### Proximity audio is now stereo

Music boxes used to mix the left and right channels together for anyone not
wearing headphones, because OpenAL will only place a sound in the world if the
sound is mono. Collapsing a stereo mix that way cancels out anything the artist
panned wide, which made synth-heavy stations sound thin and hollow.

A stereo station now gets two voices instead of one, carrying a channel each and
sitting a little over a block apart on either side of the box. Both are mono, so
distance falloff and surround placement work exactly as before, but the stereo
image survives. The pair is spread across your line of sight rather than along a
fixed compass direction, so the width holds up from wherever you are standing.

Buffering also went from about 560 ms to 740 ms, which should mean fewer clicks
when a stream stutters.

### Add your own stations in game

Players with permission get a + button in the music box GUI that opens a name
and URL form. Where a new station ends up is up to the server:

```json
"customStations": {
  "permission": "ops",
  "scope": "block",
  "maxPerBlock": 16,
  "allowedDomains": []
}
```

- `permission` is `off`, `ops` or `all`. On `ops`, the host of a single-player
  world counts too, so the button still works without enabling cheats.
- `scope` is `block` to store the station on that one music box, or `global` to
  append it to `stations.json` for every box on the server.
- `allowedDomains` restricts submissions to the listed hosts and their
  subdomains. Leave it empty to allow any host.

Submissions are checked server side for permission, for the player actually
being at the box, for an http or https URL, and against the domain list.
Choosing a station is still sent as an index that the server resolves against
its own data, so this does not let a modified client make a box play whatever
it likes.

Note that if you set `permission` to `all`, whoever hosts a submitted stream
can see the IP address of every player whose client tunes in.

### Stations

- Added SomaFM Vaporwaves.
- Groove Salad, Drone Zone, Underground 80s and DEF CON now use SomaFM's
  256 kbps streams instead of 128 kbps.

Existing servers keep their current list. `stations.json` is only written when
it is missing, so delete it or edit the entries by hand to pick these up.

## 1.0.1

- Fixed the optional Curios dependency range never matching a real build, which
  logged an unsupported-dependency warning on every startup.

## 1.0.0

- First release. Music box block, headphones with Curios and Trinkets support,
  pairing, and a configurable station list for Forge and Fabric on 1.19.2.
