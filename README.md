# Java P2P File Sharing System

A simple **Peer-to-Peer (P2P) file sharing system** implemented in Java using **sockets** and a centralized **tracker**.  
Peers can register, log in, share files, discover available files, and download them directly from other peers.

---

# Overview

The system consists of:

- **Tracker** – maintains user accounts, active peers, and file availability.
- **Peers** – clients that share files and download files from other peers.

Each peer maintains a **shared directory** containing the files it is willing to share with the network.

Typical workflow:

1. A peer registers and logs in to the tracker.
2. The peer informs the tracker about the files available in its shared directory.
3. A peer can request a list of all available files in the system.
4. When requesting details for a specific file, the tracker returns peers that own the file.
5. The requesting peer evaluates available peers and selects the best one.
6. The file is downloaded **directly from another peer**.

---

# Features

- Peer registration and authentication
- Token-based session management
- File discovery through the tracker
- Peer-to-peer file downloads
- Peer availability checks (`checkActive`)
- Download reliability metrics
- Multi-threaded tracker and peer listeners
- Concurrent data structures to prevent race conditions

---

# Project Structure


```text
project-root
│
├── settings.json
│
├── Compile-StartServer.bat
│
├── StartClient1.bat
├── StartClient2.bat
├── StartClient3.bat
├── StartClient4.bat
├── StartClient5.bat
└── StartClient6.bat
│
├── peer_shared_files
│ ├── shared_files_peer_1
│ ├── shared_files_peer_2
│ ├── shared_files_peer_3
│ ├── shared_files_peer_4
│ ├── shared_files_peer_5
│ └── shared_files_peer_6
│
└── src
  │
  ├── tracker
  │ ├── Tracker.java
  │ └── ServerEntryPoint.java
  │
  ├── peer
  │ ├── Peer.java
  │ ├── PeerController.java
  │ ├── PeerListener.java
  │ ├── PeerSettings.java
  │ └── ClientEntryPoint.java
  │
  ├── messaging
  │ ├── Message.java
  │ ├── MessageType.java
  │ └── StatusCode.java
  │
  └── model
    ├── Account.java
    ├── ContactInfo.java
    └── ContactInfoDTO.java
```


---

# Configuration

The project includes a **`settings.json`** file that defines the **source paths and configuration used by the IDE and project environment**.

---

# Core Components

## Tracker

The tracker is responsible for:

- Managing registered users (`Account`)
- Maintaining active peers (`token_id`, `ip`, `port`)
- Tracking file availability
- Responding to file queries (`list` and `details`)
- Verifying peer availability (`checkActive`)
- Updating download statistics

To support multiple simultaneous peers, the tracker uses:

- **multithreading**
- **ConcurrentHashMap** data structures

---

## Peer

Each peer is divided into three main components.

### PeerController
Handles **interaction with the user via the console**, simplifying the main entry point.

### Peer
Handles **network communication** with the tracker and other peers and manages message exchange.

### PeerListener
Runs a **server socket** that listens for incoming requests such as:

- file download requests from other peers
- `checkActive` messages

The listener runs in a **separate thread**, allowing the console interface to remain responsive.

---

## Messaging System

Communication between peers and the tracker is handled through the **`Message`** class.

Messages:

- implement `Serializable`
- contain different fields depending on their type
- support operations such as:


```text
register
login
logout
list
details
checkActive
simpleDownload
notify
```

---

## Data Models

### Account
Represents a registered user and stores:

- username
- password
- download count
- failure count

These metrics are used to evaluate peers during downloads.

---

### ContactInfo
Represents an **active peer** and contains:

- IP address
- port
- username
- access to account download/failure metrics

---

### ContactInfoDTO
A **Data Transfer Object (DTO)** used when sending peer information through the network.  
It avoids exposing internal objects and guarantees safe serialization.

---

# Peer Selection Algorithm

When a peer wants to download a file, it evaluates the available peers using the following score:


score = response_time × (0.75^count_downloads × 1.25^count_failures)


The peer with the **lowest score** is selected.

This algorithm:

- **rewards reliable peers** that successfully serve files
- **penalizes peers with previous failures**

---

# Running the Project

## 1. Start the Tracker

Run:


Compile-StartServer.bat


This script:

1. Opens the `src` directory  
2. Compiles all Java source files  
3. Starts the tracker server  

---

## 2. Start Peers

Start one or more peers by running the corresponding script:


```text
StartClient1.bat
StartClient2.bat
StartClient3.bat
StartClient4.bat
StartClient5.bat
StartClient6.bat
```


Each script launches a peer with its own:

- port
- shared directory
- runtime configuration

Running multiple scripts simulates a **multi-peer P2P network**.

---

# Development Notes

- Implemented in **Java**
- Developed using the **Cursor IDE**
- Uses **multithreading** to support concurrent peer connections
- Each peer acts both as:
  - a **client** (communicating with the tracker)
  - a **server** (serving files to other peers)

---

# Design Notes

Key design considerations:

- Keeping **minimal internal state per class** to reduce side effects
- Using **DTO objects** for network communication
- Centralizing socket communication in a single `send` method
- Running listeners and server components in **separate threads** to keep the console responsive
- Using **ConcurrentHashMap** to avoid race conditions in shared tracker data
