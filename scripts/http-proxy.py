#!/usr/bin/env python3
"""Simple HTTP CONNECT proxy for Docker containers on bridge networks."""
import socket, threading, select, sys

def handle(client):
    try:
        request = client.recv(8192)
        if not request:
            client.close(); return
        first_line = request.split(b'\r\n')[0].decode('utf-8', errors='replace')
        method = first_line.split()[0]
        if method == 'CONNECT':
            host_port = first_line.split()[1]
            host, port = host_port.split(':')
            port = int(port)
            try:
                remote = socket.create_connection((host, port), timeout=10)
            except:
                client.sendall(b'HTTP/1.1 502 Bad Gateway\r\n\r\n')
                client.close(); return
            client.sendall(b'HTTP/1.1 200 Connection Established\r\n\r\n')
            socks = [client, remote]
            while True:
                r, _, e = select.select(socks, [], socks, 60)
                if e or not r: break
                for s in r:
                    data = s.recv(65536)
                    if not data: client.close(); remote.close(); return
                    (remote if s is client else client).sendall(data)
            client.close(); remote.close()
    except:
        try: client.close()
        except: pass

server = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
server.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
server.bind(('172.19.0.1', 3128))
server.listen(100)
print('HTTP CONNECT Proxy on 172.19.0.1:3128', flush=True)
while True:
    c, _ = server.accept()
    threading.Thread(target=handle, args=(c,), daemon=True).start()
