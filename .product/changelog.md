# Changelog

## [1.2.0]

### Gobal

- Extensive debug logging that is managed by a debug_logging=true setting in config.
- Improved build process to clear libs folder before building and support build number as a version parameter

### TL 

- Support reading and taking from lectern.
- Reset cooldown when TL book is taken.
- Enhanced chest refill logic to ensure exact item counts are maintained


### TC
- Implemented placement memory for Travelers Chest
- Refactor TC to use mixin

## [1.1.3] - 2024-12-21

### Changed 
- Updated package namespace from com.arka to com.gbti
- Changed author structure in fabric.mod.json to list gbti-network as author and others as contributors
- Testing github actions

### Added
- Added support for [page] marker to force page breaks in journal content
- Added quote handling in book content using Unicode quotation marks
- Added alternating opening/closing quotation marks for better readability
- Added build script for server testing
- Added release script for pushing to GitHub and creating releases
- Making sure that lists only propagate if there is a space following a dash, but not when the - and the next character are connected. This allows coordinates with -x y z

## [1.0.1] - 2024-12-15

Prototyping: See dev_drarka.md