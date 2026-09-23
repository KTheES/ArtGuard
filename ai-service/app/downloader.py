import ipaddress
import socket
import time
from urllib.parse import urlsplit
import urllib3
from .config import Settings
from .errors import ServiceError


def parse_url(url: str):
    if any(ord(c) < 33 for c in url) or "\\" in url:
        raise ServiceError("INVALID_IMAGE_URL", "The image URL is invalid.")
    try:
        parsed = urlsplit(url)
        if parsed.scheme not in ("https", "http") or not parsed.hostname or parsed.username or parsed.password or parsed.fragment:
            raise ValueError()
        host = parsed.hostname.lower().rstrip(".")
        port = parsed.port or (443 if parsed.scheme == "https" else 80)
        if not 1 <= port <= 65535:
            raise ValueError()
        return parsed, host, port
    except ValueError:
        raise ServiceError("INVALID_IMAGE_URL", "The image URL is invalid.") from None


class ImageDownloader:
    def __init__(self, settings: Settings):
        self.settings = settings
        self.allowed = set()
        for origin in settings.allowed_origins:
            parsed, host, port = parse_url(origin)
            if parsed.path not in ("", "/") or parsed.query:
                raise ValueError("Allowed image origins must not contain a path or query")
            self.allowed.add((parsed.scheme, host, port))

    def target(self, url: str):
        parsed, host, port = parse_url(url)
        if (parsed.scheme, host, port) not in self.allowed:
            raise ServiceError("IMAGE_ORIGIN_DENIED", "The image storage origin is not allowed.", 403)
        if parsed.scheme != "https" and not self.settings.allow_private_images:
            raise ServiceError("IMAGE_ORIGIN_DENIED", "HTTPS is required.", 403)
        try:
            addresses = sorted({entry[4][0] for entry in socket.getaddrinfo(host, port, type=socket.SOCK_STREAM)})
        except OSError:
            raise ServiceError("IMAGE_DOWNLOAD_FAILED", "The image storage is unavailable.", 502) from None
        if not addresses:
            raise ServiceError("IMAGE_DOWNLOAD_FAILED", "The image storage is unavailable.", 502)
        for address in addresses:
            ip = ipaddress.ip_address(address)
            if ip.version == 6 and ip.ipv4_mapped:
                ip = ip.ipv4_mapped
            forbidden = (ip.is_link_local or ip.is_multicast or ip.is_unspecified
                         or (ip.is_reserved and not ip.is_loopback)
                         or str(ip) == "fd00:ec2::254")
            permitted_private = self.settings.allow_private_images and (ip.is_private or ip.is_loopback)
            if forbidden or not (ip.is_global or permitted_private):
                raise ServiceError("IMAGE_ADDRESS_DENIED", "The image network address is not allowed.", 403)
        # Resolve once and connect to this checked IP, preserving Host/TLS SNI.
        return parsed, host, port, addresses[0]

    def fetch(self, url: str) -> tuple[bytes, str]:
        parsed, host, port, address = self.target(url)
        kwargs = dict(host=address, port=port, maxsize=1, block=True,
                      timeout=urllib3.Timeout(connect=3, read=5, total=self.settings.download_timeout))
        if parsed.scheme == "https":
            pool = urllib3.HTTPSConnectionPool(**kwargs, server_hostname=host, assert_hostname=host, cert_reqs="CERT_REQUIRED")
        else:
            pool = urllib3.HTTPConnectionPool(**kwargs)
        default_port = 443 if parsed.scheme == "https" else 80
        authority = f"[{host}]" if ":" in host else host
        if port != default_port:
            authority += f":{port}"
        path = parsed.path or "/"
        if parsed.query:
            path += "?" + parsed.query
        deadline = time.monotonic() + self.settings.download_timeout
        response = None
        try:
            response = pool.urlopen("GET", path, headers={"Host": authority, "Accept-Encoding": "identity"},
                                    retries=False, redirect=False, preload_content=False, decode_content=False)
            if response.status != 200:
                raise ServiceError("IMAGE_DOWNLOAD_FAILED", "The image storage did not return an image.", 502)
            content_type = response.headers.get("Content-Type", "").split(";")[0].strip().lower()
            if content_type not in ("image/png", "image/jpeg") or response.headers.get("Content-Encoding", "identity") != "identity":
                raise ServiceError("INVALID_IMAGE", "Only uncompressed PNG or JPEG responses are supported.")
            length = response.headers.get("Content-Length")
            if length is not None:
                try:
                    expected = int(length)
                except ValueError:
                    raise ServiceError("INVALID_IMAGE", "The image length is invalid.") from None
                if expected <= 0 or expected > self.settings.max_image_bytes:
                    raise ServiceError("IMAGE_TOO_LARGE", "The image exceeds the size limit.", 413)
            body = bytearray()
            while True:
                if time.monotonic() > deadline:
                    raise ServiceError("IMAGE_DOWNLOAD_TIMEOUT", "The image download timed out.", 504)
                chunk = response.read1(min(65536, self.settings.max_image_bytes - len(body) + 1), decode_content=False)
                if not chunk:
                    break
                body.extend(chunk)
                if len(body) > self.settings.max_image_bytes:
                    raise ServiceError("IMAGE_TOO_LARGE", "The image exceeds the size limit.", 413)
            if not body:
                raise ServiceError("INVALID_IMAGE", "The image is empty.")
            return bytes(body), content_type
        except urllib3.exceptions.TimeoutError:
            raise ServiceError("IMAGE_DOWNLOAD_TIMEOUT", "The image download timed out.", 504) from None
        except (urllib3.exceptions.HTTPError, OSError):
            raise ServiceError("IMAGE_DOWNLOAD_FAILED", "The image download failed.", 502) from None
        finally:
            if response is not None:
                response.close()
            pool.close()
