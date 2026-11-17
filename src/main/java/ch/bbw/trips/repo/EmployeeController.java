package ch.bbw.trips.repo;

import ch.bbw.trips.ex.TriptNotFoundException;
import ch.bbw.trips.storage.FileStorageService;
import lombok.AllArgsConstructor;
import lombok.Data;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.MalformedURLException;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.core.io.Resource;

@RestController
@RequestMapping("/v1")
public class EmployeeController {
	private static final Logger log = LoggerFactory.getLogger(EmployeeController.class);

	@Autowired
	private EmployeeRepository employeeRepository;

	@Autowired
	private PasswordEncoder passwordEncoder;

	@Autowired
	private FileStorageService fileStorageService;

	@PostMapping("/signIn")
	@ResponseStatus(HttpStatus.OK)
	ResponseEntity<TokenResponse> signIn(@RequestBody Employee authenticationUser) {
		Optional<Employee> userOptional = employeeRepository.findUserByUsername(authenticationUser.getUsername());

		if (userOptional.isPresent()) {
			Employee user = userOptional.get();
			if (passwordEncoder.matches(authenticationUser.getPassword(), user.getPassword())) {
				String uuid = UUID.randomUUID().toString();
				user.setToken(uuid);
				employeeRepository.save(user);
				log.info("User logged in: " + user);

				TokenResponse response = new TokenResponse(uuid);
				return ResponseEntity
						.status(HttpStatus.OK)
						.header("Authorization", "Bearer " + uuid)
						.body(response);
			}
		}
		return ResponseEntity.notFound().build();
	}

	// POJO to represent the response body
	@Data
	@AllArgsConstructor
	private static class TokenResponse {
		private String token;
	}

	@GetMapping("/employees")
	List<Employee> allEmployees() {
		return (List<Employee>) employeeRepository.findAll();
	}

	@PostMapping("/employees")
	Employee createEmployee(@RequestBody Employee newItem) {
		if (newItem.getPassword() != null) {
			newItem.setPassword(passwordEncoder.encode(newItem.getPassword()));
		}
		return employeeRepository.save(newItem);
	}

	@GetMapping("/employees/{id}")
	Employee one(@PathVariable Long id) {
		return employeeRepository.findById(id).orElseThrow(() -> new TriptNotFoundException(id));
	}

	@PutMapping("/employees/{id}")
	Employee updateEmployee(@RequestBody Employee newItem, @PathVariable Long id) {
		return employeeRepository.findById(id).map(item -> {
			item.setName(newItem.getName());
			item.setJobTitle(newItem.getJobTitle());
			return employeeRepository.save(item);
		}).orElseGet(() -> {
			newItem.setId(id);
			return employeeRepository.save(newItem);
		});
	}

	@DeleteMapping("/employees/{id}")
	void deleteEmployee(@PathVariable Long id) {
		employeeRepository.deleteById(id);
	}

	@DeleteMapping("/employees")
	void deleteAllEmployees() {
		employeeRepository.deleteAll();
	}

	@PostMapping("/employees/{id}/photo")
	public ResponseEntity<Void> uploadPhoto(@PathVariable Long id, @RequestParam("file") MultipartFile file) throws IOException {
		Employee employee = employeeRepository.findById(id)
			.orElseThrow(() -> new TriptNotFoundException(id));
		String filename = fileStorageService.store(file);
		employee.setProfilePictureFilename(filename);
		employeeRepository.save(employee);
		return ResponseEntity.ok().build();
	}

	@GetMapping("/employees/{id}/photo")
	public ResponseEntity<Resource> downloadPhoto(@PathVariable Long id) throws MalformedURLException {
		Employee employee = employeeRepository.findById(id)
			.orElseThrow(() -> new TriptNotFoundException(id));
		if (employee.getProfilePictureFilename() == null) {
			return ResponseEntity.notFound().build();
		}
		Resource resource = fileStorageService.load(employee.getProfilePictureFilename());
		return ResponseEntity.ok()
			.contentType(MediaType.IMAGE_JPEG)
			.body(resource);
	}

}
