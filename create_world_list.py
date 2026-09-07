import struct

# Match exact blurite world_list.ws format
ip = b'127.0.0.1'
name = b'OpenRune Server'
properties = struct.pack('<I', 42467329)  # Little-endian

# Build world entry
world_entry = ip + b'\x00' + name + b'\x00' + properties

# Header: 4-byte length + 2-byte version + 4-byte flags + 1-byte world_count
length = len(world_entry)
header = struct.pack('<I', length)      # Length (little-endian)
header += struct.pack('<H', 1)          # Version
header += struct.pack('<I', 0x0288)     # Flags
header += struct.pack('<B', 1)          # World count

data = header + world_entry
with open('C:\Users\yourname/Documents/OpenRune-Server/.data/world_list.ws', 'wb') as f:
    f.write(data)
print(f'Written {len(data)} bytes')
print('Hex:', data.hex())
