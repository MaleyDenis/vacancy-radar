package radar.web;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import radar.model.UserProfile;
import radar.repository.JsonRepository;

@RestController
public class ProfileController {

  private final JsonRepository repository;

  public ProfileController(JsonRepository repository) {
    this.repository = repository;
  }

  @GetMapping("/api/profile")
  public UserProfile get() {
    return repository.readProfile();
  }

  @PutMapping("/api/profile")
  public UserProfile update(@RequestBody UserProfile profile) {
    repository.writeProfile(profile);
    return repository.readProfile();
  }
}
