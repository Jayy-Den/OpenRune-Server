import struct

# Match exact blurite world_list.ws format:
# 0000 0021 0001 00ff 0288 0001 3132 372e 302e 302e 3100 4465 7665 6c6f 706d 656e 7400 0100 00
# Length = 33 (0x21)
# Format:
#   - 4 bytes: payload length (big-endian)
#   - 2 bytes: version (0x0001)
#   - 4 bytes: flags (0x00ff0288)
#   - 2 bytes: world count (0x0001)
#   - IP (null-terminated ASCII): 127.0.0.1\0
#   - Name (null-terminated ASCII): Development\0
#   - 3 bytes: 0x01 0x00 0x00

ip = b'127.0.0.1'
name = b'OpenRune Server'  # 16 chars + null = 17 bytes

# Build payload
payload = b''
payload += struct.pack('>H', 1)  # version
payload += struct.pack('>I', 0x00ff0288)  # flags
payload += struct.pack('>H', 1)  # world count
payload += ip + b'\x00'  # IP
payload += name + b'\x00'  # Name
payload += b'\x01\x00\x00'  # trailer

length = len(payload)
packet = struct.pack('>I', length) + payload

with open('.data/world_list.ws', 'wb') as f:
    f.write(packet)

print(f'Written {len(packet)} bytes (payload={length})')
print(f'Hex: {packet.hex()}')
