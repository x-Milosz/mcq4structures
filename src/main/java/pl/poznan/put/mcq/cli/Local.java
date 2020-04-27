package pl.poznan.put.mcq.cli;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.tuple.Pair;
import org.w3c.dom.svg.SVGDocument;
import pl.poznan.put.atom.AtomName;
import pl.poznan.put.atom.AtomType;
import pl.poznan.put.atom.Bond;
import pl.poznan.put.circular.Angle;
import pl.poznan.put.circular.samples.AngleSample;
import pl.poznan.put.comparison.MCQ;
import pl.poznan.put.comparison.local.LocalComparator;
import pl.poznan.put.comparison.local.ModelsComparisonResult;
import pl.poznan.put.comparison.local.SelectedAngle;
import pl.poznan.put.comparison.mapping.AngleDeltaMapper;
import pl.poznan.put.comparison.mapping.ComparisonMapper;
import pl.poznan.put.comparison.mapping.RangeDifferenceMapper;
import pl.poznan.put.interfaces.Exportable;
import pl.poznan.put.matching.FragmentMatch;
import pl.poznan.put.matching.ResidueComparison;
import pl.poznan.put.matching.StructureSelection;
import pl.poznan.put.pdb.analysis.MoleculeType;
import pl.poznan.put.pdb.analysis.PdbCompactFragment;
import pl.poznan.put.pdb.analysis.PdbResidue;
import pl.poznan.put.rna.torsion.RNATorsionAngleType;
import pl.poznan.put.svg.SecondaryStructureVisualizer;
import pl.poznan.put.torsion.AverageTorsionAngleType;
import pl.poznan.put.torsion.MasterTorsionAngleType;
import pl.poznan.put.utility.svg.Format;
import pl.poznan.put.utility.svg.SVGHelper;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@SuppressWarnings({"CallToSystemExit", "UseOfSystemOutOrSystemErr"})
public final class Local {
  private static final Options OPTIONS =
      new Options()
          .addOption(Helper.OPTION_TARGET)
          .addOption(Helper.OPTION_SELECTION_TARGET)
          .addOption(Helper.OPTION_SELECTION_MODEL)
          .addOption(Helper.OPTION_ANGLES)
          .addOption(Helper.OPTION_NAMES)
          .addOption(Helper.OPTION_DIRECTORY);

  private Local() {
    super();
  }

  public static void main(final String[] args) throws ParseException, IOException {
    if (Helper.isHelpRequested(args)) {
      Helper.printHelp("mcq-local", Local.OPTIONS);
      return;
    }

    final CommandLineParser parser = new DefaultParser();
    final CommandLine commandLine = parser.parse(Local.OPTIONS, args);

    if (commandLine.getArgs().length < 1) {
      System.err.println("No models provided for comparison");
      System.exit(1);
    }

    final StructureSelection target = Helper.selectTarget(commandLine);
    final List<StructureSelection> models = Helper.selectModels(commandLine);

    Local.printBondLengthViolations(models);

    // check for gaps
    final int size = target.getCompactFragments().size();
    final List<StructureSelection> invalidModels =
        models.stream()
            .filter(selection -> selection.getCompactFragments().size() != size)
            .collect(Collectors.toList());
    if (!invalidModels.isEmpty()) {
      Local.printFragmentDetails(target, invalidModels);
      System.exit(1);
    }

    // check for size
    final int expectedSize = target.getResidues().size();
    models
        .parallelStream()
        .filter(selection -> selection.getResidues().size() != expectedSize)
        .peek(
            selection ->
                System.err.printf(
                    "The following structure has different size (%d) than the target (%d)%n",
                    selection.getResidues().size(), expectedSize))
        .findAny()
        .ifPresent(selection -> System.exit(1));

    // prepare MCQ instance
    final List<MasterTorsionAngleType> angleTypes = Helper.parseAngles(commandLine);
    angleTypes.add(new AverageTorsionAngleType(MoleculeType.RNA, angleTypes));
    final LocalComparator mcq = new MCQ(angleTypes);

    final List<List<Angle>> partialDifferences =
        IntStream.range(0, models.size())
            .<List<Angle>>mapToObj(i -> new ArrayList<>())
            .collect(Collectors.toList());

    final File outputDirectory = Helper.getOutputDirectory(commandLine);
    FileUtils.forceMkdir(outputDirectory);

    for (int i = 0; i < size; i++) {
      final PdbCompactFragment targetFragment = target.getCompactFragments().get(i);

      final List<PdbCompactFragment> modelFragments = new ArrayList<>();
      for (final StructureSelection model : models) {
        modelFragments.add(model.getCompactFragments().get(i));
      }

      // rename
      if (size == 1) {
        targetFragment.setName(target.getName());
        IntStream.range(0, models.size())
            .forEach(j -> modelFragments.get(j).setName(models.get(j).getName()));
      } else {
        Local.renameFragment(targetFragment, target);
        IntStream.range(0, models.size())
            .forEach(j -> Local.renameFragment(modelFragments.get(j), models.get(j)));
      }

      final ModelsComparisonResult comparisonResult =
          mcq.compareModels(targetFragment, modelFragments);
      Local.exportResults(outputDirectory, comparisonResult);
      System.out.println("Partial results are available in: " + outputDirectory);

      for (int j = 0; j < comparisonResult.getFragmentMatches().size(); j++) {
        final FragmentMatch match = comparisonResult.getFragmentMatches().get(j);
        partialDifferences
            .get(j)
            .addAll(
                match.getResidueComparisons().stream()
                    .map(ResidueComparison::extractValidDeltas)
                    .flatMap(Collection::stream)
                    .collect(Collectors.toList()));
      }
    }

    final Set<Pair<Double, StructureSelection>> ranking;
    final List<Angle> mcqs =
        partialDifferences.stream()
            .map(AngleSample::new)
            .map(AngleSample::getMeanDirection)
            .collect(Collectors.toList());
    ranking =
        IntStream.range(0, models.size())
            .mapToObj(i -> Pair.of(mcqs.get(i).getDegrees(), models.get(i)))
            .collect(Collectors.toCollection(TreeSet::new));

    for (final Pair<Double, StructureSelection> pair : ranking) {
      System.out.printf(Locale.US, "%s %.2f%n", pair.getValue().getName(), pair.getKey());
    }
  }

  private static void renameFragment(
      final PdbCompactFragment fragment, final StructureSelection selection) {
    selection.setName(String.format("%s %s", selection.getName(), fragment.getName()));
  }

  private static void printFragmentDetails(
      final StructureSelection target, final Iterable<? extends StructureSelection> models) {
    final StringBuilder builder = new StringBuilder();
    builder.append("Fragments in reference structure: (").append(target.getName()).append(")\n");
    target
        .getCompactFragments()
        .forEach(fragment -> builder.append("- ").append(fragment).append('\n'));

    Local.printGapsDetails(target, builder);
    builder.append('\n');

    for (final StructureSelection model : models) {
      builder.append("Fragments in model: (").append(model.getName()).append(")\n");
      model
          .getCompactFragments()
          .forEach(fragment -> builder.append("- ").append(fragment).append('\n'));

      Local.printGapsDetails(model, builder);
      builder.append('\n');
    }

    System.err.print(builder);
  }

  private static void printGapsDetails(
      final StructureSelection selection, final StringBuilder builder) {

    final List<PdbResidue> residues = selection.getResidues();
    boolean foundGaps = false;

    for (int i = 1; i < residues.size(); i++) {
      final PdbResidue previous = residues.get(i - 1);
      final PdbResidue current = residues.get(i);

      // skip check if any of the residues has icode
      if (StringUtils.isNotBlank(previous.getInsertionCode())
          || StringUtils.isNotBlank(current.getInsertionCode())) {
        continue;
      }

      // skip check if residues are in different chains
      if (!previous.getChainIdentifier().equals(current.getChainIdentifier())) {
        continue;
      }

      // skip check if residues are not consecutive
      if ((previous.getResidueNumber() + 1) != current.getResidueNumber()) {
        continue;
      }

      if (!MoleculeType.RNA.areConnected(previous, current)) {
        final String reason;
        if (!previous.hasAtom(AtomName.O3p)) {
          reason = "first residue lacks O3' atom";
        } else if (!current.hasAtom(AtomName.P)) {
          reason = "second residue lacks P atom";
        } else {
          final Bond.Length length = Bond.length(AtomType.O, AtomType.P);
          reason =
              String.format(
                  Locale.US,
                  "O3'-P distance is %.2f but should be [%.2f; %.2f]",
                  previous.findAtom(AtomName.O3p).distanceTo(current.findAtom(AtomName.P)),
                  length.getMin(),
                  length.getMax());
        }

        if (!foundGaps) {
          builder.append(String.format("Found gaps: (%s)%n", selection.getName()));
          foundGaps = true;
        }
        builder.append(String.format("- Between %s and %s: %s%n", previous, current, reason));
      }
    }
  }

  private static void exportResults(
      final File outputDirectory, final ModelsComparisonResult comparisonResult) {
    try {
      Local.exportTable(outputDirectory, comparisonResult);
      comparisonResult
          .getFragmentMatches()
          .parallelStream()
          .forEach(fragmentMatch -> Local.exportModelResults(outputDirectory, fragmentMatch));
    } catch (final IOException e) {
      throw new IllegalArgumentException("Failed to export results", e);
    }
  }

  private static void exportTable(
      final File directory, final ModelsComparisonResult comparisonResult) throws IOException {
    final File file = new File(directory, "table.csv");
    try (final OutputStream stream = new FileOutputStream(file)) {
      final SelectedAngle selectedAngle =
          comparisonResult.selectAngle(RNATorsionAngleType.getAverageOverMainAngles());
      selectedAngle.export(stream);
    }
  }

  private static void exportModelResults(
      final File parentDirectory, final FragmentMatch fragmentMatch) {
    try {
      final String name = fragmentMatch.getModelFragment().getName();
      final File directory = new File(parentDirectory, name);
      FileUtils.forceMkdir(directory);

      Local.exportSecondaryStructureImage(
          fragmentMatch, directory, "delta.svg", AngleDeltaMapper.getInstance());
      Local.exportSecondaryStructureImage(
          fragmentMatch, directory, "range.svg", RangeDifferenceMapper.getInstance());
      Local.exportDifferences(fragmentMatch, directory);
    } catch (final IOException e) {
      throw new IllegalArgumentException("Failed to export results", e);
    }
  }

  private static void exportSecondaryStructureImage(
      final FragmentMatch fragmentMatch,
      final File directory,
      final String filename,
      final ComparisonMapper deltaMapper)
      throws IOException {
    final SVGDocument svg = SecondaryStructureVisualizer.visualize(fragmentMatch, deltaMapper);
    final File file = new File(directory, filename);

    try (final OutputStream stream = new FileOutputStream(file)) {
      stream.write(SVGHelper.export(svg, Format.SVG));
    }
  }

  private static void exportDifferences(final Exportable fragmentMatch, final File directory)
      throws IOException {
    final File file = new File(directory, "differences.csv");
    try (final OutputStream stream = new FileOutputStream(file)) {
      fragmentMatch.export(stream);
    }
  }

  private static void printBondLengthViolations(
      final Iterable<? extends StructureSelection> selections) {
    final StringBuilder builder = new StringBuilder();

    for (final StructureSelection selection : selections) {
      final List<String> violations = selection.findBondLengthViolations();

      if (!violations.isEmpty()) {
        builder
            .append("Found bond length violations in ")
            .append(selection.getName())
            .append(":\n");
        violations.forEach(violation -> builder.append("- ").append(violation).append('\n'));
        builder.append('\n');
      }
    }

    System.err.println(builder);
  }
}
