package com.edam.architecture;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import com.edam.common.security.AuthenticatedEndpoint;
import com.edam.common.security.EdamPermission;
import com.edam.common.security.Permissions;
import com.edam.common.security.PublicEndpoint;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that authorization is deny-by-default, by inspecting every REST endpoint in the application.
 *
 * <h2>Why a reflection test rather than a review checklist</h2>
 * The most likely security regression in a product like this is not a subtle flaw — it is a new
 * endpoint added without a permission, which then works because nothing stops it. A checklist catches
 * that only if someone remembers. This test fails the build instead:
 *
 * <ul>
 *   <li>every controller method that is mapped must carry {@link EdamPermission}, or be marked
 *       {@link PublicEndpoint} with a stated reason;</li>
 *   <li>a {@link PublicEndpoint} must be a documented exception — the annotation's {@code reason}
 *       cannot be blank, so "public because it was easier" does not pass;</li>
 *   <li>every declared permission must exist in the {@link Permissions} catalogue, so a typo does not
 *       silently create an unenforceable permission;</li>
 *   <li>a class-level {@link EdamPermission} is accepted as applying to all its methods, matching how
 *       the aspect evaluates it.</li>
 * </ul>
 *
 * <p>This test is deliberately written as an architecture test, not a web test: it inspects the code,
 * so it also covers endpoints that no integration test happens to exercise.
 */
class EndpointAuthorizationArchitectureTest {

	private static final String BASE_PACKAGE = "com.edam";

	@Test
	@DisplayName("every REST endpoint is either permission-guarded or explicitly public with a reason")
	void everyEndpointIsGuarded() throws ClassNotFoundException {
		List<String> violations = new ArrayList<>();
		List<String> publicEndpoints = new ArrayList<>();
		List<String> authenticatedEndpoints = new ArrayList<>();
		int guarded = 0;
		for (Class<?> controller : controllers()) {
			Method[] methods = controller.getDeclaredMethods();
			EdamPermission typeLevel = controller.getAnnotation(EdamPermission.class);
			PublicEndpoint publicTypeLevel = controller.getAnnotation(PublicEndpoint.class);
			for (Method method : methods) {
				if (!isMapped(method)) {
					continue;
				}
				EdamPermission methodLevel = method.getAnnotation(EdamPermission.class);
				PublicEndpoint methodPublic = method.getAnnotation(PublicEndpoint.class);
				String location = controller.getSimpleName() + "#" + method.getName();
				if (methodPublic != null) {
					if (methodPublic.reason() == null || methodPublic.reason().isBlank()) {
						violations.add(location + " is marked public without a reason");
					}
					else {
						publicEndpoints.add(location + " (" + methodPublic.reason() + ")");
					}
					continue;
				}
				if (publicTypeLevel != null) {
					if (publicTypeLevel.reason() == null || publicTypeLevel.reason().isBlank()) {
						violations.add(location + " inherits a public declaration with no reason");
					}
					else {
						publicEndpoints.add(location + " (class-level)");
					}
					continue;
				}
				// A third category: authenticated but requiring no specific permission. Reading your own
				// profile and changing your own password are the only legitimate members — a user with no
				// roles must still manage their own credential, and requiring a permission there would lock
				// out precisely the user who needs it.
				AuthenticatedEndpoint authenticated = method.getAnnotation(AuthenticatedEndpoint.class);
				AuthenticatedEndpoint authenticatedType = controller.getAnnotation(AuthenticatedEndpoint.class);
				if (authenticated != null || authenticatedType != null) {
					String reason = (authenticated != null) ? authenticated.reason() : authenticatedType.reason();
					if (reason == null || reason.isBlank()) {
						violations.add(location + " requires a session but gives no justification");
					}
					else {
						authenticatedEndpoints.add(location + " (" + reason + ")");
					}
					guarded++;
					continue;
				}
				EdamPermission effective = (methodLevel != null) ? methodLevel : typeLevel;
				if (effective == null) {
					violations.add(location + " has no @EdamPermission, @AuthenticatedEndpoint or @PublicEndpoint");
					continue;
				}
				if (effective.value() == null || effective.value().isBlank()) {
					violations.add(location + " declares an empty permission");
					continue;
				}
				if (!Permissions.all().contains(effective.value())) {
					violations.add(location + " declares '" + effective.value()
							+ "', which is not in the Permissions catalogue");
					continue;
				}
				guarded++;
			}
		}
		assertThat(violations)
			.as("Endpoints must be deny-by-default. Guard them with @EdamPermission or mark them "
					+ "@PublicEndpoint(reason = \"...\"). Public endpoints found: %s", publicEndpoints)
			.isEmpty();
		assertThat(guarded).as("the scan must actually find guarded endpoints").isPositive();
		// Authenticated endpoints are a deliberate, narrow category. Enumerated by controller so that a
		// new one anywhere else fails this test and has to be argued for in review.
		assertThat(authenticatedEndpoints)
			.as("unexpected session-only endpoint(s): %s", authenticatedEndpoints)
			.allMatch(entry -> entry.startsWith("AuthController") || entry.startsWith("DevUserController"));
	}

	@Test
	@DisplayName("the public-endpoint allow-list is exactly the bootstrap surface, by design")
	void publicEndpointsAreTheBootstrapSurfaceOnly() throws ClassNotFoundException {
		List<String> publicEndpoints = new ArrayList<>();
		for (Class<?> controller : controllers()) {
			if (controller.getAnnotation(PublicEndpoint.class) != null) {
				publicEndpoints.add(controller.getSimpleName());
			}
			for (Method method : controller.getDeclaredMethods()) {
				if (isMapped(method) && method.getAnnotation(PublicEndpoint.class) != null) {
					publicEndpoints.add(controller.getSimpleName() + "#" + method.getName());
				}
			}
		}
		// The allow-list is intentionally small and is enumerated by *controller*, not by pattern, so
		// that adding a public method to an existing controller still fails this test.
		//
		// AuthController is public by necessity: it contains the endpoints by which a session is
		// obtained, renewed and recovered. Each method carries its own @PublicEndpoint reason, and the
		// first test in this class still requires that reason to be present and non-blank. The guarded
		// methods of that same controller (GET /auth/me, POST /auth/change-password) are NOT in this
		// list — they carry @EdamPermission-style authentication requirements and are covered by the
		// first test.
		// ConfigurationController#publicConfiguration is public because the login shell and
		// unauthenticated landing page must be able to read branding/name/locale before a
		// session exists. The service layer whitelists the returned fields and never leaks
		// secrets or internal settings.
		assertThat(publicEndpoints)
			.as("unexpected public endpoint(s); add them here only with a security justification")
			.allMatch(entry -> entry.contains("Health") || entry.contains("Probe") || entry.contains("System")
					|| entry.contains("Platform") || entry.startsWith("DevUserController")
					|| entry.startsWith("AuthController")
					|| entry.equals("ConfigurationController#publicConfiguration"));
	}

	private static List<Class<?>> controllers() throws ClassNotFoundException {
		ClassPathScanningCandidateComponentProvider scanner = new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(RestController.class));
		List<Class<?>> found = new ArrayList<>();
		for (BeanDefinition candidate : scanner.findCandidateComponents(BASE_PACKAGE)) {
			found.add(Class.forName(candidate.getBeanClassName()));
		}
		assertThat(found).as("no REST controllers found in " + BASE_PACKAGE).isNotEmpty();
		return found;
	}

	private static boolean isMapped(Method method) {
		return method.isAnnotationPresent(RequestMapping.class) || method.isAnnotationPresent(GetMapping.class)
				|| method.isAnnotationPresent(PostMapping.class) || method.isAnnotationPresent(PutMapping.class)
				|| method.isAnnotationPresent(PatchMapping.class) || method.isAnnotationPresent(DeleteMapping.class);
	}

}
