import struct

# Exact blurite world_list.ws format:
# 0000 0021 0001 00ff 0288 0001 3132 372e 302e 302e 3100 4465 7665 6c6f 706d 656e 7400 0100 00
# Length = 33 (0x21)
# Format after length:
#   - 2 bytes: 0x0001 (version)
#   - 4 bytes: 0x00ff0288 (flags, big-endian!)
#   - 2 bytes: 0x0001 (world count)
#   - IP null-terminated
#   - Name null-terminated
#   - 3 bytes: 0x010000 (trailer)

ip = b'127.0.0.1'
name = b'OpenRune Server'

# Build after length field - use big-endian for flags
data = struct.pack('>H', 1)           # version = 0x0001 (big-endian)
data += struct.pack('>I', 0x00ff0288) # flags = 0x00ff0288 (big-endian!)
data += struct.pack('>H', 1)          # world count = 0x0001 (big-endian)
data += ip + b'\x00'                  # IP null-terminated
data += name + b'\x00'                # Name null-terminated
data += b'\x01\x00\x00'              # Trailer

length = len(data)
packet = struct.pack('>I', length) + data  # Length in big-endian

with open('C:\Users\yourname/Documents/OpenRune-Server/.data/world_list.ws', 'wb') as f:
    f.write(packet)

print(f'Written {len(packet)} bytes (length={length})')
print(f'Hex: {packet.hex()}')
