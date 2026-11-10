# Pin Glide Android Sender

The Android context describes forwarding from the current device to its paired target devices.

## Language

**Sender**:
The current device that forwards newly received SMS messages to its paired targets.

**Sender registration**:
A Sender's identity within one Account. Permanent revocation closes that identity; expiration or replacement of its access credentials does not by itself close the registration.

**Sender restoration context**:
The protected Account and Sender identifiers retained after ordinary logout. Only a verified login to that same Account and Sender may reattach its retained Pairing keys and receive preferences. The context contains no service credentials.

**Target device**:
A device selected as a destination for messages from the current Sender.
_Avoid_: Computer, phone, Windows endpoint in generic UI copy; user-chosen aliases are preserved.

**Pairing**:
The relationship through which one Sender can forward encrypted messages to one target device. Removing that relationship does not remove other Senders' relationships with the same target.

**Pairing key**:
The secret shared by the two participants of a Pairing for protecting their messages. A local key failure means the current participant's copy is missing or unusable; it says nothing about the other participant's copy.

**Test message**:
A simulated verification message initiated by the user to check delivery from the current Sender to a paired target device.
