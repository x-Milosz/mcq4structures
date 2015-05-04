package pl.poznan.put.gui.panel;

import java.awt.BorderLayout;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JProgressBar;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.UIManager;
import javax.swing.border.EmptyBorder;
import javax.swing.table.DefaultTableCellRenderer;

import pl.poznan.put.comparison.ComparisonListener;
import pl.poznan.put.comparison.GlobalComparisonMeasure;
import pl.poznan.put.comparison.GlobalComparisonResultMatrix;
import pl.poznan.put.comparison.ParallelGlobalComparator;
import pl.poznan.put.gui.ProcessingResult;
import pl.poznan.put.matching.SelectionFactory;
import pl.poznan.put.matching.StructureSelection;
import pl.poznan.put.pdb.analysis.PdbModel;
import pl.poznan.put.structure.tertiary.StructureManager;

public class GlobalMatrixPanel extends JPanel implements ComparisonListener {
    private final JTextPane labelInfoMatrix = new JTextPane();
    private final JTable tableMatrix = new JTable();
    private final JProgressBar progressBar = new JProgressBar();

    private List<PdbModel> structures = Collections.emptyList();

    public GlobalMatrixPanel() {
        super(new BorderLayout());

        labelInfoMatrix.setBorder(new EmptyBorder(10, 10, 10, 0));
        labelInfoMatrix.setContentType("text/html");
        labelInfoMatrix.setEditable(false);
        labelInfoMatrix.setFont(UIManager.getFont("Label.font"));
        labelInfoMatrix.setOpaque(false);
        progressBar.setStringPainted(true);

        JPanel panelInfo = new JPanel(new BorderLayout());
        panelInfo.add(labelInfoMatrix, BorderLayout.CENTER);

        JPanel panelProgressBar = new JPanel();
        panelProgressBar.setLayout(new BoxLayout(panelProgressBar, BoxLayout.X_AXIS));
        panelProgressBar.add(new JLabel("Progress in computing:"));
        panelProgressBar.add(progressBar);

        add(panelInfo, BorderLayout.NORTH);
        add(new JScrollPane(tableMatrix), BorderLayout.CENTER);
        add(panelProgressBar, BorderLayout.SOUTH);
    }

    public void setStructures(List<PdbModel> structures) {
        this.structures = structures;
        updateHeader(false, "");
    }

    @Override
    public void stateChanged(long all, long completed) {
        progressBar.setMaximum((int) all);
        progressBar.setValue((int) completed);
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

    public ProcessingResult compareAndDisplayMatrix(
            GlobalComparisonMeasure measure) {
        List<StructureSelection> selections = new ArrayList<>();

        for (int i = 0; i < structures.size(); i++) {
            PdbModel structure = structures.get(i);
            String name = StructureManager.getName(structure);
            selections.add(SelectionFactory.create(name, structure));
        }

        ParallelGlobalComparator comparator = ParallelGlobalComparator.getInstance(measure);
        GlobalComparisonResultMatrix matrix = comparator.run(selections, this);

        tableMatrix.setModel(matrix.asDisplayableTableModel());
        tableMatrix.setDefaultRenderer(Object.class, new DefaultTableCellRenderer());
        updateHeader(true, matrix.getMeasureName());

        return new ProcessingResult(matrix);
    }
}
