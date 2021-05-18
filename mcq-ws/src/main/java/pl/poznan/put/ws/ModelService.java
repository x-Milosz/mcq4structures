package pl.poznan.put.ws;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import pl.poznan.put.ws.model.Torsion;
import pl.poznan.put.ws.model.Version;

@Service
public class ModelService {

  private Version version;

  @Autowired
  public ModelService(Version version) {
    this.version = version;
  }

  public Version findVersion() {
    return version;
  }

  public Torsion findTorsion(String pdbId, Integer assemblyId) {
    return null;
  }

  public void addTorsion(Torsion newTorsion) {}
}
