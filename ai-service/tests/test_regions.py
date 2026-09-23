from PIL import Image
from app.regions import crop_regions

def test_fixed_regions_are_deterministic_and_have_expected_pixel_bounds():
    image = Image.new("RGB", (200, 100), "white")
    crops = crop_regions(image)
    assert [region.key for region, _ in crops] == ["CENTER", "TOP_LEFT", "TOP_RIGHT", "BOTTOM_LEFT", "BOTTOM_RIGHT"]
    assert crops[0][1].size == (130, 65)
    assert all(crop.size == (130, 65) for _, crop in crops)
