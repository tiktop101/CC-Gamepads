# CC: Gamepads

CC: Gamepads adds a CC:Tweaked peripheral block which lets ComputerCraft computers read real gamepad/controller input from players.

The controller is read on the player client, sent to the server, and exposed to Lua through a bound Gamepad Peripheral. This works in singleplayer, LAN, and dedicated multiplayer servers.

## Requirements

- Minecraft 1.21.1
- NeoForge 21.1.x
- CC:Tweaked 1.117.1

## How To Use

1. Place a `Gamepad Peripheral` block next to, or connected by modem to, a CC:Tweaked computer.
2. Hold a `Gamepad` item.
3. Sneak-right-click the Gamepad Peripheral with the Gamepad item to bind it.
4. Keep the bound Gamepad item in your main hand or off hand.
5. Plug in/connect with bluetooth your/a controller and run Lua code on the computer.

Up to 4 players can send input to the same Gamepad Peripheral at once. Each player gets a slot from 1 to 4.

## Lua Peripheral

Wrap the peripheral like any other CC:Tweaked peripheral:

```lua
local pad = peripheral.find("gamepad")
```

## Functions

### `getPlayers()`

Returns a list of connected controller states.

Each entry includes:

- `slot`: player slot, 1 to 4
- `player`: player name
- `playerId`: player UUID
- `controllerId`: client controller id
- `controllerName`: controller name
- `guid`: controller GUID
- `buttons`: numbered button table
- `buttonNames`: named button table
- `axes`: numbered axis table
- `axisNames`: named axis table
- `updatedAt`: server timestamp

### `getState(slot)`

Returns the full state table for one slot.

Throws an error if no controller is bound to that slot.

### `isDown(slot, button)`

Returns `true` if a numbered button is held.

Buttons:

1. A
2. B
3. X
4. Y
5. Left bumper
6. Right bumper
7. Back/select
8. Start
9. Guide/home
10. Left stick
11. Right stick
12. D-pad up
13. D-pad right
14. D-pad down
15. D-pad left

### `isButtonDown(slot, name)`

Returns `true` if a named button is held.

Names:

```text
a b x y
leftBumper rightBumper
back select start guide home
leftStick rightStick
dpadUp dpadRight dpadDown dpadLeft
```

### `getAxis(slot, axis)`

Returns a numbered axis value.

Axes:

1. Left X, -1 to 1
2. Left Y, -1 to 1
3. Right X, -1 to 1
4. Right Y, -1 to 1
5. Left trigger, 0 to 1
6. Right trigger, 0 to 1

### `getAxisValue(slot, name)`

Returns a named axis value.

Names:

```text
leftX leftY rightX rightY leftTrigger rightTrigger
```

### `getMaxPlayers()`

Returns `4`.

### `getButtonCount()`

Returns `15`.

### `getAxisCount()`

Returns `6`.

## Events

When controller state changes, attached computers receive:

```lua
cc_events, "gamepad", slot, playerName, controllerId
```

Example:

```lua
local event, kind, slot, player, controllerId = os.pullEvent("cc_events")
if kind == "gamepad" then
  print(player .. " updated slot " .. slot)
end
```

## Example Lua Program

This prints basic state for slot 1 and exits when Start is pressed.

```lua
local pad = peripheral.find("gamepad")
if not pad then
  error("No gamepad peripheral found")
end

print("Waiting for gamepad input...")

while true do
  local event, kind, slot, player = os.pullEvent("cc_events")
  if kind == "gamepad" and slot == 1 then
    local lx = pad.getAxisValue(1, "leftX")
    local ly = pad.getAxisValue(1, "leftY")
    local a = pad.isButtonDown(1, "a")
    local b = pad.isButtonDown(1, "b")

    term.clear()
    term.setCursorPos(1, 1)
    print("Player: " .. player)
    print(("Left stick: %.2f %.2f"):format(lx, ly))
    print("A: " .. tostring(a))
    print("B: " .. tostring(b))
    print("Start exits")

    if pad.isButtonDown(1, "start") then
      break
    end
  end
end
```

## Multiplayer Notes

- Controller input is only accepted from the player who sent it.
- The player must be holding a Gamepad item bound to the target peripheral.
- Controller IDs and GUIDs are verified server-side.
- Packet sizes, string lengths, button counts, axis counts, and axis values are validated before the state is accepted.
- Player state is cleared when they disconnect or when their controller list changes.

## Controller Mappings

The mod includes a bundled SDL game controller database for common Windows, Linux, and macOS controllers.

Clients can add extra mappings, if needed here:

```text
config/ccgamepads/gamecontrollerdb.txt
```

