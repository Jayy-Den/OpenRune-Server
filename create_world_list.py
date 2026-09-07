import struct

# Exact blurite world_list.ws format (from https://client.blurite.io/world_list.ws):
# 0000 0021 0001 00ff 0288 0001 3132 372e 302e 302e 3100 4465 7665 6c6f 706d 656e 7400 0100 00
# Length = 33 (0x21)
# Format:
#   - 4 bytes: payload length (big-endian)
#   - 2 bytes: version (0x0001, big-endian)
#   - 4 bytes: flags (0x00ff0288, big-endian)
#   - 2 bytes: world count (0x0001, big-endian)
#   - IP (null-terminated ASCII)
#   - Name (null-terminated ASCII)
#   - 3 bytes: 0x01 0x00 0x00 (trailer)

ip = b'127.0.0.1'
name = b'OpenRune Server'

# Build payload (after the 4-byte length field)
payload = b''
payload += struct.pack('>H', 1)  # version = 1
payload += struct.pack('>I', 0x00ff0288)  # flags
payload += struct.pack('>H', 1)  # world count = 1
payload += ip + b'\x00'  # IP null-terminated
payload += name + b'\x00'  # Name null-terminated
payload += b'\x01\x00\x00'  # trailer

length = len(payload)
packet = struct.pack('>I', length) + payload

with open('C:\Users\yourname/Documents/OpenRune-Server/.data/world_list.ws', 'wb') as f:
    f.write(packet)

print(f'Written {len(packet)} bytes (payload={length})')
print(f'Hex: {packet.hex()}')
print(f'Expected payload length: 33')
print(f'My payload length: {length}')
print(f'Match: {length == 33}')
