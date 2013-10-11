package pl.poznan.put.cs.bioserver.torsion;

import org.biojava.bio.structure.Group;

import pl.poznan.put.cs.bioserver.helper.UniTypeQuadruplet;

public class AngleAverageAll extends AbstractAngleType {
    private static AngleAverageAll instance = new AngleAverageAll();

    public static AngleAverageAll getInstance() {
        return AngleAverageAll.instance;
    }

    private AngleAverageAll() {
    }

    @Override
    public String getAngleDisplayName() {
        return "Average of all angles";
    }

    @Override
    public String getAngleName() {
        return "AVG_ALL";
    }

    @Override
    public UniTypeQuadruplet<String> getAtomNames(Group residue) {
        throw new UnsupportedOperationException(
                "An average of angular values is a meta-entry "
                        + "which is not bound to specific atoms");
    }

    @Override
    public UniTypeQuadruplet<Integer> getGroupRule() {
        throw new UnsupportedOperationException(
                "An average of angular values is a meta-entry "
                        + "which is not bound to specific atoms");
    }
}
