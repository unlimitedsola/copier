# Copier

_A fast parallel copying utility._

[![License](https://img.shields.io/github/license/unlimitedsola/copier?style=flat-square)](https://github.com/unlimitedsola/copier/blob/master/LICENSE.txt)
[![Version](https://img.shields.io/github/release/unlimitedsola/copier?style=flat-square)](https://github.com/unlimitedsola/copier/releases)

## Features

- Multiple destinations: supports coping from one source to multiple destinations at a time. the source will be only read once for multiple destinations.
- Parallel coping: multiple destinations copying is done in completely parallel, no time are wasted. 
- Direct I/O access: uses [Windows Direct I/O Access](https://support.microsoft.com/en-us/help/100027/info-direct-drive-access-under-win32) for reading and writing, no random accesses.

## User Guide

0. Requires Java Runtime Environment(JRE) 1.8 with JavaFX bundled
1. Download the latest release from [release page](https://github.com/unlimitedsola/copier/releases)
2. Execute the downloaded jar file

## Supported Platforms

Windows only. more platforms might be added in the future.
