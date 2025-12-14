##### it's currently really raw and wip, might fix it later, or might not

# Cataclysm
a papermc 1.21.8 plugin for an UHC like minigame with a twist: random cataclysms like solar activity, acid rain and blood moon. and if you die - you die, and now have to fly around in spectator, watching other players

## events
currently there are three events:
* blood moon - occurs every full moon; mobs get stronger, creepers get charged.
* acid rain - actively makes your armor decay, and you as well, unless you wear full leather armor.
* solar activity - actively melts you and your armor, unless you wear full iron armor (don't ask me...).

## what's wrong
i've made that plugin literally for two damn sessions with my friends, and since no one wants one more sesh, i've decided to abandon it for some time. currently some strings are still hardcoded and stuff, some features might be broken too, and there's still a plenty of room for more events or fixes. if you wan't to modify it and use for your server, feel free, just remember to follow the license and credit me or something.

## commands
game related:
* `/cataclysm team <red, green, blue, yellow> <Player1> [Player2] ...` - assigns player(s) to the selected team
* `/cataclysm trigger start` - starts the game
* `/cataclysm trigger event <blood_moon, acid_rain, solar_activity>` - starts the selected event
* `/cataclysm trigger event stop` - stops the current event
* `/cataclysm trigger stop` - stops the game

config related:
* `/cataclysm setspawn <red, green, blue, yellow, lobby>` - sets the spawnpoint for the team
* `/cataclysm reload` - reload the config/lang
