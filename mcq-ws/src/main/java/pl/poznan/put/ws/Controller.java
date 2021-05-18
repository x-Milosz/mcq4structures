package pl.poznan.put.ws;

import jakarta.validation.constraints.NotEmpty;
import org.hibernate.validator.constraints.Length;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;
import pl.poznan.put.ws.model.Torsion;
import pl.poznan.put.ws.model.Version;

import javax.validation.Valid;
import javax.validation.constraints.Positive;

@RestController
@RequestMapping("/api")
@Validated
public class Controller {

  private ModelService modelService;

  @Autowired
  public Controller(ModelService modelService) {
    this.modelService = modelService;
  }

  public Controller() {}

  @GetMapping("/version")
  private Version getVersion() {
    return modelService.findVersion();
  }

  @PostMapping("/torsion")
  private ResponseEntity<?> postTorsion(@Valid @RequestBody Torsion torsion) {
    modelService.addTorsion(torsion);
    return ResponseEntity.ok().build();
  }

  @GetMapping("/torsion/{pdbId}")
  private Torsion getTorsion(@PathVariable("pdbId") @Length(min = 4, max = 4) String pdbId) {
    return modelService.findTorsion(pdbId, 1);
  }

  @GetMapping("/torsion/{pdbId}/{assemblyId}")
  private Torsion getTorsion(
      @PathVariable @Length(min = 4, max = 4) @NotEmpty String pdbId,
      @PathVariable @Positive Integer assemblyId) {
    return modelService.findTorsion(pdbId, assemblyId);
  }
}
