package pl.poznan.put.gui.panel;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.svg.SVGDocument;

import pl.poznan.put.circular.exception.InvalidCircularValueException;
import pl.poznan.put.comparison.GlobalComparisonMeasure;
import pl.poznan.put.comparison.GlobalComparisonResultMatrix;
import pl.poznan.put.comparison.ParallelGlobalComparator;
import pl.poznan.put.gui.ProcessingResult;
import pl.poznan.put.matching.SelectionFactory;
import pl.poznan.put.matching.StructureSelection;
import pl.poznan.put.pdb.analysis.PdbModel;
import pl.poznan.put.structure.tertiary.StructureManager;
import pl.poznan.put.utility.svg.SVGHelper;
import pl.poznan.put.visualisation.MatrixVisualizationComponent;

public class GlobalMatrixPanel extends JPanel {
    public interface Callback {
        void complete(ProcessingResult processingResult);
    }

    private static final Logger LOGGER = LoggerFactory.getLogger(GlobalMatrixPanel.class);

    private final JTextPane labelInfoMatrix = new JTextPane();
    private final JTable tableMatrix = new JTable();
    private final JProgressBar progressBar = new JProgressBar(0, 1);
    private final MatrixVisualizationComponent visualization = new MatrixVisualizationComponent(SVGHelper.emptyDocument());

    private List<PdbModel> structures = Collections.emptyList();

    public GlobalMatrixPanel() {
        super(new BorderLayout());

        labelInfoMatrix.setBorder(new EmptyBorder(10, 10, 10, 0));
        labelInfoMatrix.setContentType("text/html");
        labelInfoMatrix.setEditable(false);
        labelInfoMatrix.setFont(UIManager.getFont("Label.font"));
        labelInfoMatrix.setOpaque(false);

        JPanel panelInfo = new JPanel(new BorderLayout());
        panelInfo.add(labelInfoMatrix, BorderLayout.CENTER);

        JPanel panelProgressBar = new JPanel(new BorderLayout());
        panelProgressBar.add(progressBar, BorderLayout.CENTER);

        JTabbedPane tabbedPane = new JTabbedPane();
        tabbedPane.add("Distance matrix", new JScrollPane(tableMatrix));
        tabbedPane.add("Visualization", visualization);

        add(panelInfo, BorderLayout.NORTH);
        add(tabbedPane, BorderLayout.CENTER);
        add(panelProgressBar, BorderLayout.SOUTH);
    }

    public void setStructures(List<PdbModel> structures) {
        this.structures = structures;
        updateHeader(false, "");
    }

    public void updateHeader(boolean readyResults, String measureName) {
        StringBuilder builder = new StringBuilder();
        builder.append("<html>Structures selected for global distance measure: ");
        int i = 0;

        for (PdbModel s : structures) {
            assert s != null;
            builder.append("<span style=\"color: " + (i % 2 == 0 ? "blue" : "green") + "\">");
            builder.append(StructureManager.getName(s));
            builder.append("</span>, ");
            i++;
        }

        builder.delete(builder.length() - 2, builder.length());

        if (readyResults) {
            builder.append("<br>Global distance matrix (");
            builder.append(measureName);
            builder.append("):");
        }

        builder.append("</html>");
        labelInfoMatrix.setText(builder.toString());
    }

    public void compareAndDisplayMatrix(GlobalComparisonMeasure measure,
            final Callback callback) {
        try {
            List<StructureSelection> selections = new ArrayList<>();

            for (int i = 0; i < structures.size(); i++) {
                PdbModel structure = structures.get(i);
                String name = StructureManager.getName(structure);
                selections.add(SelectionFactory.create(name, structure));
            }

            progressBar.setMinimum(0);
            progressBar.setMaximum(structures.size() * (structures.size() - 1) / 2);
            progressBar.setValue(0);

            ParallelGlobalComparator comparator = new ParallelGlobalComparator(measure, selections, new ParallelGlobalComparator.ProgressListener() {
                @Override
                public void setProgress(int progress) {
                    progressBar.setValue(progress);
                }

                @Override
                public void complete(GlobalComparisonResultMatrix matrix) {
                    SVGDocument document = matrix.visualize();

                    tableMatrix.setModel(matrix.asDisplayableTableModel());
                    tableMatrix.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
                    visualization.setSVGDocument(document);
                    updateHeader(true, matrix.getMeasureName());
                    callback.complete(new ProcessingResult(matrix));
                }
            });

            comparator.start();
        } catch (InvalidCircularValueException e) {
            String message = "Failed to compare structures";
            GlobalMatrixPanel.LOGGER.error(message, e);
            JOptionPane.showMessageDialog(this, message, "Error", JOptionPane.ERROR_MESSAGE);
        }
    }
}
