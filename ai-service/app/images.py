from io import BytesIO
import warnings
from PIL import Image, ImageOps, UnidentifiedImageError
from .errors import ServiceError


def decode_image(data: bytes, content_type: str, max_pixels: int = 16_000_000) -> Image.Image:
    def invalid():
        return ServiceError("INVALID_IMAGE", "A valid PNG or JPEG within the image limits is required.")
    expected = {"image/png": "PNG", "image/jpeg": "JPEG"}.get(content_type)
    if expected is None:
        raise invalid()
    try:
        with warnings.catch_warnings():
            warnings.simplefilter("error", Image.DecompressionBombWarning)
            with Image.open(BytesIO(data)) as image:
                if image.format != expected:
                    raise invalid()
                width, height = image.size
                if width < 1 or height < 1 or max(width, height) > 8192 or width * height > max_pixels:
                    raise invalid()
                image.verify()
            with Image.open(BytesIO(data)) as image:
                image.load()
                image = ImageOps.exif_transpose(image)
                rgba = image.convert("RGBA")
                background = Image.new("RGBA", rgba.size, "white")
                background.alpha_composite(rgba)
                return background.convert("RGB")
    except (UnidentifiedImageError, OSError, ValueError, Image.DecompressionBombError, Image.DecompressionBombWarning):
        raise invalid() from None
