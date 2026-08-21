# Changelog

## 1.2.0

### The block does something while it plays

The music box front now carries a record that spins while a station is playing,
its label cycling through neon, and both sides carry a five band spectrum
display built out of stepped LEDs.

The bars are driven by a real FFT of the audio rather than a canned animation,
and getting that to look right needed more care than it sounds. Audio is
analysed as it is handed to OpenAL, but OpenAL is sitting on roughly three
quarters of a second of queued buffers, so a naive visualiser runs almost a
second ahead of the music. The analysis is instead queued alongside the audio
and advanced by OpenAL's own report of which buffers it has finished, so a bar
moves at the moment you hear the sound that moved it.

A box you cannot hear stays dark rather than miming, since the levels come from
the stream your client is actually playing.

### Speakers

A new black speaker block relays a music box to somewhere else. Use a speaker on
a music box to pair it, the same gesture that pairs headphones, then place it;
the link is stored on the item, so a whole stack can be paired once and then put
up around a room. A placed speaker can be re-pointed by using it, which lists the
music boxes within 24 blocks and gives it its own volume.

Speakers are proximity sources at their own position, and their cone is pushed
out of the cabinet by the low end of whatever is playing.

Pairing is resolved server side and copied onto the speaker, so a speaker keeps
working when its box is far away, in an unloaded chunk, or in another dimension.

### One connection per station, and emitters that stay together

A music box and the speakers relaying it now share a single decoded stream
rather than each opening their own.

This was necessary for speakers to be usable at all. Two independent connections
to the same station arrive at different points in the broadcast, so two speakers
in one room would have comb filtered into something that sounded like a broken
echo. It also means the mod holds one connection per station no matter how many
blocks are playing it, which matters because plenty of stations cap connections
per address.

An emitter that joins a stream already in progress fills to the same buffer depth
as one already playing before it starts, rather than merely to the prebuffer, so
placing a speaker next to a running one does not leave it permanently offset.

### Pairing no longer needs a sneak

Using headphones or a speaker on a music box pairs them on an ordinary right
click. Previously the box was offered the click first and opened its panel, which
ate the interaction before the item ever saw it, so the only way through was to
sneak. The box now steps aside when you are holding something pairable.

This is a behaviour change for headphones, which needed the sneak in 1.1.0. The
sneak still works, so nothing anyone had learned has been taken away.

### Volume sliders stay where you put them

Both panels read the volume back from the server every frame so they pick up
changes made by someone else. That read was overriding the slider while it was
being dragged, and again for the length of a round trip after it was let go, so
on the speaker panel the knob sprang straight back and the control looked dead.
The read is now held off while you are mid-change.

Volume is also sent as you drag rather than only when you let go, so you can hear
what you are choosing, at one update per five percent step.

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
