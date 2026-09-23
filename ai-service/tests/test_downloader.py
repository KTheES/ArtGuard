from dataclasses import replace
from unittest.mock import patch
import socket
import pytest
from app.config import Settings
from app.downloader import ImageDownloader
from app.errors import ServiceError

SETTINGS = Settings(api_key="test-only-api-key-0123456789abcdef", allowed_origins=("https://storage.test",))


def records(*ips):
    return [(socket.AF_INET, socket.SOCK_STREAM, 6, "", (ip, 443)) for ip in ips]


@pytest.mark.parametrize("url", ["http://storage.test/a", "https://other.test/a", "https://storage.test.evil/a", "https://storage.test:444/a"])
def test_exact_origin_allowlist(url):
    with pytest.raises(ServiceError):
        ImageDownloader(SETTINGS).target(url)


@pytest.mark.parametrize("url", ["file:///tmp/image", "https://user:pass@storage.test/a", "https://storage.test/a#fragment", "https://storage.test/\nsecret"])
def test_malformed_url(url):
    with pytest.raises(ServiceError):
        ImageDownloader(SETTINGS).target(url)


@pytest.mark.parametrize("ip", ["127.0.0.1", "10.0.0.1", "169.254.169.254", "::1", "::ffff:127.0.0.1", "0.0.0.0"])
def test_internal_dns_is_denied(ip):
    with patch("socket.getaddrinfo", return_value=records(ip)), pytest.raises(ServiceError):
        ImageDownloader(SETTINGS).target("https://storage.test/image")


def test_mixed_public_private_dns_is_denied():
    with patch("socket.getaddrinfo", return_value=records("8.8.8.8", "127.0.0.1")), pytest.raises(ServiceError):
        ImageDownloader(SETTINGS).target("https://storage.test/image")


def test_local_mode_still_denies_cloud_metadata():
    settings = replace(SETTINGS, allow_private_images=True)
    with patch("socket.getaddrinfo", return_value=records("169.254.169.254")), pytest.raises(ServiceError):
        ImageDownloader(settings).target("https://storage.test/image")


def test_public_address_is_pinned():
    with patch("socket.getaddrinfo", return_value=records("8.8.8.8")) as dns:
        parsed, host, port, address = ImageDownloader(SETTINGS).target("https://storage.test/image?signature=secret")
        assert address == "8.8.8.8" and host == "storage.test" and port == 443
        dns.assert_called_once()


def test_redirect_is_not_followed():
    with patch("socket.getaddrinfo", return_value=records("8.8.8.8")), patch("urllib3.HTTPSConnectionPool") as pool:
        pool.return_value.urlopen.return_value.status = 302
        with pytest.raises(ServiceError):
            ImageDownloader(SETTINGS).fetch("https://storage.test/image")
        assert pool.return_value.urlopen.call_args.kwargs["redirect"] is False
        assert pool.call_args.kwargs["host"] == "8.8.8.8"
        assert pool.call_args.kwargs["server_hostname"] == "storage.test"


def test_declared_oversize_is_rejected_before_reading():
    with patch("socket.getaddrinfo", return_value=records("8.8.8.8")), patch("urllib3.HTTPSConnectionPool") as pool:
        response = pool.return_value.urlopen.return_value
        response.status = 200
        response.headers = {"Content-Type": "image/png", "Content-Length": "999999999"}
        with pytest.raises(ServiceError):
            ImageDownloader(SETTINGS).fetch("https://storage.test/image")
        response.read1.assert_not_called()


def test_chunked_size_is_bounded_without_content_length():
    with patch("socket.getaddrinfo", return_value=records("8.8.8.8")), patch("urllib3.HTTPSConnectionPool") as pool:
        response = pool.return_value.urlopen.return_value
        response.status = 200
        response.headers = {"Content-Type": "image/png"}
        response.read1.side_effect = [b"x" * 11, b""]
        with pytest.raises(ServiceError):
            ImageDownloader(replace(SETTINGS, max_image_bytes=10)).fetch("https://storage.test/image")
