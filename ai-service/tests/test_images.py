from io import BytesIO
import numpy as np
from PIL import Image
import pytest
from app.images import decode_image
from app.engine import preprocess
from app.errors import ServiceError


def image_bytes(size=(32, 24), fmt="PNG"):
    stream = BytesIO()
    Image.new("RGB", size, "blue").save(stream, format=fmt)
    return stream.getvalue()


@pytest.mark.parametrize("fmt,mime", [("PNG", "image/png"), ("JPEG", "image/jpeg")])
def test_supported_images(fmt, mime):
    with decode_image(image_bytes(fmt=fmt), mime) as image:
        assert image.size == (32, 24) and image.mode == "RGB"


def test_transparency_uses_white_background():
    stream = BytesIO()
    Image.new("RGBA", (4, 4), (0, 0, 0, 0)).save(stream, format="PNG")
    with decode_image(stream.getvalue(), "image/png") as image:
        assert image.getpixel((0, 0)) == (255, 255, 255)


@pytest.mark.parametrize("content,mime", [(b"not-an-image", "image/png"), (image_bytes(), "image/jpeg"), (image_bytes()[:20], "image/png")])
def test_invalid_images(content, mime):
    with pytest.raises(ServiceError):
        decode_image(content, mime)


def test_dimension_limit():
    with pytest.raises(ServiceError):
        decode_image(image_bytes((8193, 1)), "image/png")


def test_preprocessing_shape_and_finite_values():
    result = preprocess(Image.new("RGB", (400, 250), "white"))
    assert result.shape == (1, 3, 224, 224)
    assert result.dtype == np.float32 and np.isfinite(result).all()


def test_extreme_aspect_ratio_cannot_allocate_unbounded_resize():
    with pytest.raises(ServiceError):
        preprocess(Image.new("RGB", (8192, 1), "white"))
