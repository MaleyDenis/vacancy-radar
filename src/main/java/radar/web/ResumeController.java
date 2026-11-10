package radar.web;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import radar.repository.JsonRepository;

@RestController
public class ResumeController {

  private final JsonRepository repository;

  public ResumeController(JsonRepository repository) {
    this.repository = repository;
  }

  @GetMapping(value = "/api/resume", produces = MediaType.TEXT_PLAIN_VALUE)
  public String get() {
    return repository.readResume();
  }

  @PutMapping(value = "/api/resume", consumes = MediaType.TEXT_PLAIN_VALUE,
      produces = MediaType.TEXT_PLAIN_VALUE)
  public String update(@RequestBody(required = false) String resume) {
    repository.writeResume(resume == null ? "" : resume.strip());
    return repository.readResume();
  }
}
