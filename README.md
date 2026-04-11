# ChatLogin

A plugin for minecraft server Vertex System

Vertex system is running on version 1.21.11 purpur

Dependencies: AuthMe

## How it works

| Situation                           | What the player sees                       |
| ----------------------------------- | ------------------------------------------ |
| First login (unregistered)          | "Create a password and type it in chat"    |
| Entered password for the first time | "Enter the password again to confirm"      |
| Passwords don't match               | "Passwords do not match, please try again" |
| Password too short                  | "Minimum 8 characters"                     |
| Re-logging in                       | "Type your password in chat"               |
| Incorrect password                  | "Incorrect password, please try again"     |

The entered password is **not visible** to other players — the message is completely cancelled.
