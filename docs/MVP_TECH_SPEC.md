# Minecraft NeoForge Resource Observer Mod - MVP Technical Specification

## 1. Document Purpose

This document defines the full MVP implementation plan for a Minecraft NeoForge mod that monitors in-game resource networks.

The document is designed for:
- Human developers
- AI agents with no prior conversation context

An implementer should be able to start coding directly from this file.

## 2. MVP Scope

### 2.1 In Scope
- Platform: `NeoForge 1.21.1`
- Java: `Java 21`
- In-game only (no web module in MVP)
- Unified binding workflow using a dedicated binding tool
- Observer block that binds to exactly one network
- Support network integrations:
- `AE2` (Applied Energistics 2) item storage network
- `Flux Networks` energy network (controller-based binding)
- Periodic server-side sampling
- Metrics:
- total production
- total consumption
- net change (production - consumption)
- In-game terminal item opens GUI
- GUI supports:
- single observer detail view
- multi-observer list
- aggregate summary across observers

### 2.2 Out of Scope (Explicitly Deferred)
- Web dashboard / HTTP service / internet access
- Authentication and remote access
- Distinguishing machine-generated resources vs player manual insertion
- Automatic bind from arbitrary nearby blocks
- Advanced chart rendering beyond basic trend display

## 3. Product Concept

The mod introduces three core gameplay objects:

1. `Observer Block`
- Stores one network binding
- Samples network data periodically
- Maintains rolling statistics

2. `Binding Tool`
- Two-step right-click binding flow
- Step 1: select observer block
- Step 2: select target network block

3. `Resource Terminal` item
- Right-click to open in-game GUI
- Shows observer data and aggregates

Design intent:
- Binding UX is unified for AE2 and Flux Networks
- Observer is read-only from external network perspective
- Integrations are adapter-based and extensible

## 4. Functional Requirements

### 4.1 Binding Workflow

The binding tool behaves as a two-step state machine:

- `IDLE`
- `OBSERVER_SELECTED`

Flow:
1. Player right-clicks an observer block with binding tool.
2. Tool stores selected observer position and dimension.
3. Player right-clicks target network block.
4. Tool resolves adapter by target block and attempts binding.
5. On success:
- observer stores bound network metadata
- tool resets to `IDLE`
6. On failure:
- observer unchanged
- tool remains usable and provides player feedback

Additional interactions:
- Sneak + right-click observer with binding tool: unbind observer.
- Sneak + air right-click: clear tool temporary selection.

### 4.2 Binding Constraints

- One observer can bind to exactly one network at a time.
- Observer network type is fixed after bind until unbind.
- Rebinding requires unbind first.
- Observer must show status:
- `UNBOUND`
- `BOUND`
- `OFFLINE`
- `ERROR`

### 4.3 Supported Target Blocks (MVP)

- AE2: valid AE2 network device that can resolve to a usable grid/node.
- Flux Networks: `Flux Controller` only in MVP.

This narrow target set reduces ambiguity and integration risk.

## 5. Architecture

## 5.1 Layered Design

- `integration`: per-mod adapters (AE2, Flux)
- `binding`: tool and bind/unbind orchestration
- `sampling`: periodic collection scheduler
- `stats`: rolling windows and daily accumulation
- `storage`: observer persistence and world index
- `ui`: terminal menu/screen and client sync
- `networking`: server-client payloads

## 5.2 Core Abstractions

```java
public interface NetworkAdapter {
    NetworkType type();
    boolean canBind(ServerLevel level, BlockPos pos);
    BoundNetwork bind(ServerLevel level, BlockPos pos) throws BindException;
    NetworkSnapshot sample(ServerLevel level, BoundNetwork bound) throws SampleException;
}
```

```java
public enum NetworkType {
    AE2_ITEMS,
    FLUX_ENERGY
}
```

```java
public record BoundNetwork(
    NetworkType type,
    String networkId,
    String displayName,
    ResourceLocation sourceMod,
    CompoundTag adapterData
) {}
```

```java
public record NetworkSnapshot(
    long gameTime,
    long epochMillis,
    NetworkType type,
    String networkId,
    Map<String, Long> metrics
) {}
```

## 5.3 Observer Block Entity Data

Each observer block entity should persist:
- `UUID observerId`
- `String customName` (optional)
- `ObserverStatus status`
- `BoundNetwork boundNetwork` (nullable)
- `NetworkSnapshot lastSnapshot` (nullable)
- `long lastSuccessSampleGameTime`
- `String lastError` (optional)
- `RollingStats stats`

`RollingStats` minimum:
- current value set (last sample)
- last minute totals
- last hour totals
- today totals
- net values for each window

## 5.4 World-Level Observer Index

Maintain a world saved-data index of all observer locations.

Purpose:
- terminal can list all observers globally
- enables aggregate views without chunk scanning
- supports future filtering/grouping

Index payload:
- dimension id
- block position
- observer id
- lightweight metadata cache (optional)

## 6. Integration Details

## 6.1 AE2 Adapter (MVP)

Binding:
- validate target block can resolve to AE2 node/grid
- persist stable network reference in `adapterData`

Sampling:
- read AE2 storage inventory snapshot via storage service
- compute total items and per-item counts

Expected snapshot metrics keys (example):
- `ae2.total_item_types`
- `ae2.total_item_amount`
- `ae2.item.<namespace:path>`

Stats derivation:
- for each item:
- positive delta contributes to production
- negative delta absolute value contributes to consumption
- net = production - consumption

Known limitation:
- inventory delta cannot distinguish manual insertion vs automation in MVP.

## 6.2 Flux Networks Adapter (MVP)

Binding:
- target must be Flux Controller
- resolve controller-associated flux network
- persist network identity in `adapterData`

Sampling metrics (minimum):
- `flux.energy_stored`
- `flux.input_per_tick`
- `flux.output_per_tick`
- `flux.plug_count`
- `flux.point_count`
- `flux.storage_count`
- `flux.controller_count`

Stats derivation:
- production accumulates from sampled input throughput over time window
- consumption accumulates from sampled output throughput over time window
- net = production - consumption

## 7. Sampling and Timing

MVP sampling interval:
- every `20 ticks` (1 second)

Rationale:
- sufficient resolution for trends
- low overhead compared to per-tick collection
- consistent for both AE2 and Flux

Scheduler:
- server-side tick event
- iterate registered active observers
- skip unbound/offline observers
- execute adapter sample

Failure policy:
- if sample fails, mark observer `ERROR` and store message
- retry next interval
- recover to `BOUND` on successful sample

## 8. Statistics Model

## 8.1 Definitions

- `production`: quantity added during window
- `consumption`: quantity removed during window
- `net`: `production - consumption`

## 8.2 Windows

MVP required windows:
- `last_minute`
- `last_hour`
- `today`

Daily boundary:
- use server local date rollover (real date) for MVP
- reset today counters at date change

## 8.3 Aggregate Rules

Terminal aggregate modes:
- all observers
- by network type

Aggregation formula:
- sum production across included observers
- sum consumption across included observers
- net = summed production - summed consumption

## 9. UI / UX Requirements

## 9.1 Resource Terminal GUI

Primary sections:
- Observer list panel
- Selected observer detail panel
- Global summary panel
- Type filter tabs (`All`, `AE2`, `Flux`)

Observer list row fields:
- observer name/id
- network type
- status
- latest net value

Observer detail fields:
- observer status
- network display name
- last sample time
- today production
- today consumption
- today net

AE2 extra fields:
- total item types
- total item amount
- top N item deltas (optional in MVP if time constrained)

Flux extra fields:
- current stored energy
- current input/t
- current output/t
- connector counts

## 9.2 Client/Server Responsibility

- Server computes and owns all statistics.
- Client requests data snapshots for GUI rendering.
- Client stores only temporary display state.

## 10. Networking (Mod Internal)

Required payloads (MVP):
- open terminal sync payload (observer summaries + aggregate summary)
- observer detail payload
- optional periodic refresh payload while GUI is open

Guidelines:
- never trust client-provided stats
- keep payload schema versioned for future expansion

## 11. Persistence

Persistence layers:
- Observer block entity NBT:
- binding
- last snapshot metadata
- rolling stats summary
- world saved-data:
- observer index

On chunk load:
- observer re-registers in index if missing

On observer break:
- remove from index

## 12. Error Handling and Player Feedback

Player-visible binding errors:
- target not supported
- target not in valid network
- observer missing/invalid
- target mod not loaded

Observer runtime errors:
- binding target destroyed/unloaded
- adapter sampling exception

Feedback channels:
- action bar or chat component for binding tool
- status text in terminal GUI

## 13. Performance and Safety Constraints

- Sampling must be O(number_of_bound_observers) per interval.
- Avoid scanning world for observers each tick.
- Use observer index for direct iteration.
- Protect against runaway memory growth:
- cap historical micro-samples retained in memory
- store aggregated windows, not unbounded raw history

## 14. Suggested Package Layout

```text
com.yourmod.resourceobserver
  api
  binding
  integration.ae2
  integration.flux
  observer
  sampling
  stats
  storage
  terminal
  networking
  registry
```

## 15. Implementation Milestones

M1 Foundation:
- NeoForge project setup
- registries and basic items/blocks
- observer block + block entity skeleton

M2 Binding:
- binding tool item
- two-step state machine
- bind/unbind commands and feedback

M3 Integration:
- network adapter abstraction
- AE2 adapter bind + sample
- Flux adapter bind + sample (controller only)

M4 Sampling + Stats:
- server sampling scheduler
- rolling stats windows
- daily counters

M5 Terminal:
- terminal item + menu/screen
- observer list + detail + aggregate
- payload sync

M6 Hardening:
- error states
- persistence validation
- basic performance checks

## 16. MVP Acceptance Criteria

MVP is complete when all are true:

1. Player can place observer block.
2. Player can bind observer using two-step binding tool flow.
3. AE2 and Flux Controller targets can be bound successfully.
4. Observer samples data every second on server.
5. Terminal GUI opens in-game and shows:
- per-observer stats
- aggregate stats
- production/consumption/net values
6. Data persists across save/load.
7. Errors are surfaced via status, not silent failure.

## 17. Post-MVP Extension Path

Planned next stage (not in MVP):
- web module with separate deployment lifecycle
- API export from server-side stats
- auth and remote viewing
- richer charts and historical analytics

This separation preserves clean boundaries:
- mod core remains gameplay-stable
- web layer evolves independently
