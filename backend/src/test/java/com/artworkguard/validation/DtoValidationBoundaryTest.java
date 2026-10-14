package com.artworkguard.validation;

import com.artworkguard.artwork.dto.ArtworkDtos;
import com.artworkguard.auth.dto.AuthDtos;
import com.artworkguard.detection.DetectionReviewDtos;
import com.artworkguard.marketplace.aliexpress.AliDtos;
import com.artworkguard.product.dto.CatalogDtos;
import com.artworkguard.takedown.TakedownDtos;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.UUID;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DtoValidationBoundaryTest {
    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void openValidator() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void closeValidator() {
        factory.close();
    }

    @ParameterizedTest(name = "signup {0}")
    @MethodSource("signupCases")
    void validatesSignupBoundaries(String name, AuthDtos.SignupRequest request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> signupCases() {
        return Stream.of(
            args("ordinary", new AuthDtos.SignupRequest("artist@example.com", "ValidPass123!", "Artist"), true),
            args("email normalization", new AuthDtos.SignupRequest("  ARTIST@EXAMPLE.COM  ", "ValidPass123!", "Artist"), true),
            args("plus address", new AuthDtos.SignupRequest("artist+alerts@example.com", "ValidPass123!", "Artist"), true),
            args("subdomain", new AuthDtos.SignupRequest("artist@studio.example.com", "ValidPass123!", "Artist"), true),
            args("minimum password characters", new AuthDtos.SignupRequest("artist@example.com", "123456789012", "Artist"), true),
            args("maximum ASCII password", new AuthDtos.SignupRequest("artist@example.com", "a".repeat(72), "Artist"), true),
            args("maximum UTF-8 password", new AuthDtos.SignupRequest("artist@example.com", "가".repeat(24), "Artist"), true),
            args("nickname trim", new AuthDtos.SignupRequest("artist@example.com", "ValidPass123!", "  Artist  "), true),
            args("null email", new AuthDtos.SignupRequest(null, "ValidPass123!", "Artist"), false),
            args("blank email", new AuthDtos.SignupRequest("   ", "ValidPass123!", "Artist"), false),
            args("malformed email", new AuthDtos.SignupRequest("artist.example.com", "ValidPass123!", "Artist"), false),
            args("null password", new AuthDtos.SignupRequest("artist@example.com", null, "Artist"), false),
            args("short password", new AuthDtos.SignupRequest("artist@example.com", "12345678901", "Artist"), false),
            args("73 byte ASCII password", new AuthDtos.SignupRequest("artist@example.com", "a".repeat(73), "Artist"), false),
            args("75 byte UTF-8 password", new AuthDtos.SignupRequest("artist@example.com", "가".repeat(25), "Artist"), false),
            args("null nickname", new AuthDtos.SignupRequest("artist@example.com", "ValidPass123!", null), false),
            args("blank nickname", new AuthDtos.SignupRequest("artist@example.com", "ValidPass123!", "   "), false),
            args("oversized nickname", new AuthDtos.SignupRequest("artist@example.com", "ValidPass123!", "n".repeat(51)), false)
        );
    }

    @ParameterizedTest(name = "login {0}")
    @MethodSource("loginCases")
    void validatesLoginBoundaries(String name, AuthDtos.LoginRequest request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> loginCases() {
        return Stream.of(
            args("ordinary", new AuthDtos.LoginRequest("artist@example.com", "password"), true),
            args("email normalization", new AuthDtos.LoginRequest(" ARTIST@EXAMPLE.COM ", "password"), true),
            args("one character password", new AuthDtos.LoginRequest("artist@example.com", "x"), true),
            args("maximum ASCII password", new AuthDtos.LoginRequest("artist@example.com", "a".repeat(72)), true),
            args("maximum UTF-8 password", new AuthDtos.LoginRequest("artist@example.com", "가".repeat(24)), true),
            args("null email", new AuthDtos.LoginRequest(null, "password"), false),
            args("blank email", new AuthDtos.LoginRequest(" ", "password"), false),
            args("malformed email", new AuthDtos.LoginRequest("not-an-email", "password"), false),
            args("null password", new AuthDtos.LoginRequest("artist@example.com", null), false),
            args("blank password", new AuthDtos.LoginRequest("artist@example.com", " "), false),
            args("73 byte ASCII password", new AuthDtos.LoginRequest("artist@example.com", "a".repeat(73)), false),
            args("75 byte UTF-8 password", new AuthDtos.LoginRequest("artist@example.com", "가".repeat(25)), false)
        );
    }

    @ParameterizedTest(name = "upload {0}")
    @MethodSource("uploadCases")
    void validatesUploadBoundaries(String name, ArtworkDtos.UploadRequest request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> uploadCases() {
        return Stream.of(
            args("PNG minimum", new ArtworkDtos.UploadRequest("image/png", 1), true),
            args("JPEG minimum", new ArtworkDtos.UploadRequest("image/jpeg", 1), true),
            args("PNG maximum", new ArtworkDtos.UploadRequest("image/png", 20L * 1024 * 1024), true),
            args("JPEG ordinary", new ArtworkDtos.UploadRequest("image/jpeg", 1024), true),
            args("null content type", new ArtworkDtos.UploadRequest(null, 1), false),
            args("blank content type", new ArtworkDtos.UploadRequest(" ", 1), false),
            args("GIF rejected", new ArtworkDtos.UploadRequest("image/gif", 1), false),
            args("SVG rejected", new ArtworkDtos.UploadRequest("image/svg+xml", 1), false),
            args("uppercase type rejected", new ArtworkDtos.UploadRequest("IMAGE/PNG", 1), false),
            args("type parameters rejected", new ArtworkDtos.UploadRequest("image/png; charset=utf-8", 1), false),
            args("zero bytes", new ArtworkDtos.UploadRequest("image/png", 0), false),
            args("over maximum", new ArtworkDtos.UploadRequest("image/png", 20L * 1024 * 1024 + 1), false)
        );
    }

    @ParameterizedTest(name = "artwork create {0}")
    @MethodSource("artworkCreateCases")
    void validatesArtworkCreateBoundaries(String name, ArtworkDtos.CreateRequest request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> artworkCreateCases() {
        UUID upload = UUID.randomUUID();
        return Stream.of(
            args("ordinary", new ArtworkDtos.CreateRequest(upload, "My work", "Description"), true),
            args("minimum title", new ArtworkDtos.CreateRequest(upload, "x", ""), true),
            args("maximum title", new ArtworkDtos.CreateRequest(upload, "t".repeat(200), ""), true),
            args("null description normalized", new ArtworkDtos.CreateRequest(upload, "My work", null), true),
            args("maximum description", new ArtworkDtos.CreateRequest(upload, "My work", "d".repeat(5000)), true),
            args("trimmed title", new ArtworkDtos.CreateRequest(upload, "  My work  ", ""), true),
            args("null upload", new ArtworkDtos.CreateRequest(null, "My work", ""), false),
            args("null title", new ArtworkDtos.CreateRequest(upload, null, ""), false),
            args("blank title", new ArtworkDtos.CreateRequest(upload, "   ", ""), false),
            args("oversized title", new ArtworkDtos.CreateRequest(upload, "t".repeat(201), ""), false),
            args("oversized description", new ArtworkDtos.CreateRequest(upload, "My work", "d".repeat(5001)), false)
        );
    }

    @ParameterizedTest(name = "artwork update {0}")
    @MethodSource("artworkUpdateCases")
    void validatesArtworkUpdateBoundaries(String name, ArtworkDtos.UpdateRequest request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> artworkUpdateCases() {
        return Stream.of(
            args("version only", new ArtworkDtos.UpdateRequest(null, null, null, 0L), true),
            args("minimum title", new ArtworkDtos.UpdateRequest("x", null, null, 0L), true),
            args("maximum title", new ArtworkDtos.UpdateRequest("t".repeat(200), null, null, 1L), true),
            args("maximum description", new ArtworkDtos.UpdateRequest(null, "d".repeat(5000), null, 1L), true),
            args("enable monitoring", new ArtworkDtos.UpdateRequest(null, null, true, 1L), true),
            args("disable monitoring", new ArtworkDtos.UpdateRequest(null, null, false, 1L), true),
            args("blank title", new ArtworkDtos.UpdateRequest("   ", null, null, 0L), false),
            args("oversized title", new ArtworkDtos.UpdateRequest("t".repeat(201), null, null, 0L), false),
            args("oversized description", new ArtworkDtos.UpdateRequest(null, "d".repeat(5001), null, 0L), false),
            args("null version", new ArtworkDtos.UpdateRequest(null, null, null, null), false),
            args("negative version", new ArtworkDtos.UpdateRequest(null, null, null, -1L), false)
        );
    }

    @ParameterizedTest(name = "catalog collect {0}")
    @MethodSource("catalogCases")
    void validatesCatalogCollectionBoundaries(String name, CatalogDtos.CollectRequest request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> catalogCases() {
        return Stream.of(
            args("defaults", new CatalogDtos.CollectRequest(null, null), true),
            args("blank query", new CatalogDtos.CollectRequest("", 20), true),
            args("trimmed query", new CatalogDtos.CollectRequest("  cat art  ", 20), true),
            args("maximum query", new CatalogDtos.CollectRequest("q".repeat(100), 20), true),
            args("minimum limit", new CatalogDtos.CollectRequest("cat", 1), true),
            args("maximum limit", new CatalogDtos.CollectRequest("cat", 100), true),
            args("oversized query", new CatalogDtos.CollectRequest("q".repeat(101), 20), false),
            args("zero limit", new CatalogDtos.CollectRequest("cat", 0), false),
            args("negative limit", new CatalogDtos.CollectRequest("cat", -1), false),
            args("over maximum limit", new CatalogDtos.CollectRequest("cat", 101), false)
        );
    }

    @ParameterizedTest(name = "AliExpress search {0}")
    @MethodSource("aliSearchCases")
    void validatesAliSearchBoundaries(String name, AliDtos.SearchRequest request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> aliSearchCases() {
        return Stream.of(
            args("ordinary", new AliDtos.SearchRequest("cat art", 1, 5), true),
            args("minimum query", new AliDtos.SearchRequest("x", 1, 1), true),
            args("maximum query", new AliDtos.SearchRequest("q".repeat(100), 1, 1), true),
            args("maximum page", new AliDtos.SearchRequest("cat", 100, 1), true),
            args("maximum limit", new AliDtos.SearchRequest("cat", 1, 5), true),
            args("null query", new AliDtos.SearchRequest(null, 1, 1), false),
            args("blank query", new AliDtos.SearchRequest("   ", 1, 1), false),
            args("oversized query", new AliDtos.SearchRequest("q".repeat(101), 1, 1), false),
            args("zero page", new AliDtos.SearchRequest("cat", 0, 1), false),
            args("page over maximum", new AliDtos.SearchRequest("cat", 101, 1), false),
            args("zero limit", new AliDtos.SearchRequest("cat", 1, 0), false),
            args("limit over maximum", new AliDtos.SearchRequest("cat", 1, 6), false)
        );
    }

    @ParameterizedTest(name = "review/takedown {0}")
    @MethodSource("workflowCases")
    void validatesWorkflowBoundaries(String name, Object request, boolean valid) {
        assertValidity(request, valid);
    }

    static Stream<Arguments> workflowCases() {
        UUID evidence = UUID.randomUUID();
        return Stream.of(
            args("review new version zero", new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.NEW, 0L), true),
            args("review confirmed", new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.CONFIRMED, 3L), true),
            args("review dismissed", new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.DISMISSED, 3L), true),
            args("review null status", new DetectionReviewDtos.UpdateRequest(null, 0L), false),
            args("review null version", new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.NEW, null), false),
            args("review negative version", new DetectionReviewDtos.UpdateRequest(DetectionReviewDtos.ReviewStatus.NEW, -1L), false),
            args("takedown create", new TakedownDtos.Create(evidence, 0L, true), true),
            args("takedown null evidence", new TakedownDtos.Create(null, 0L, true), false),
            args("takedown negative detection version", new TakedownDtos.Create(evidence, -1L, true), false),
            args("takedown rights false", new TakedownDtos.Create(evidence, 0L, false), false),
            args("takedown rights null", new TakedownDtos.Create(evidence, 0L, null), false),
            args("takedown update draft", new TakedownDtos.Update(TakedownDtos.Status.DRAFT, 0L, null), true),
            args("takedown update reference max", new TakedownDtos.Update(TakedownDtos.Status.SUBMITTED, 1L, "r".repeat(200)), true),
            args("takedown update null status", new TakedownDtos.Update(null, 0L, null), false),
            args("takedown update null version", new TakedownDtos.Update(TakedownDtos.Status.DRAFT, null, null), false),
            args("takedown update negative version", new TakedownDtos.Update(TakedownDtos.Status.DRAFT, -1L, null), false),
            args("takedown update oversized reference", new TakedownDtos.Update(TakedownDtos.Status.SUBMITTED, 1L, "r".repeat(201)), false)
        );
    }

    @Test
    void signupCanonicalizesIdentityFieldsBeforeValidationAndPersistence() {
        var request = new AuthDtos.SignupRequest("  ARTIST@EXAMPLE.COM  ", "ValidPass123!", "  Artist  ");
        assertEquals("artist@example.com", request.email());
        assertEquals("Artist", request.nickname());
    }

    @Test
    void sensitiveAuthenticationDtosNeverExposeSecretsInToString() {
        var signup = new AuthDtos.SignupRequest("artist@example.com", "SecretPassword123!", "Artist");
        var login = new AuthDtos.LoginRequest("artist@example.com", "SecretPassword123!");
        var refresh = new AuthDtos.RefreshRequest("refresh-secret");
        var tokens = new AuthDtos.TokenResponse("access-secret", "refresh-secret", "Bearer", 300, 3600);
        assertTrue(signup.toString().contains("REDACTED"));
        assertTrue(login.toString().contains("REDACTED"));
        assertTrue(refresh.toString().contains("REDACTED"));
        assertTrue(tokens.toString().contains("REDACTED"));
        assertFalse(signup.toString().contains("SecretPassword123!"));
        assertFalse(login.toString().contains("SecretPassword123!"));
        assertFalse(refresh.toString().contains("refresh-secret"));
        assertFalse(tokens.toString().contains("access-secret"));
    }

    private static Arguments args(String name, Object value, boolean valid) {
        return Arguments.of(name, value, valid);
    }

    private static void assertValidity(Object value, boolean expectedValid) {
        assertEquals(expectedValid, validator.validate(value).isEmpty());
    }
}
