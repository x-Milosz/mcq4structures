package pl.poznan.put.cs.bioserver.gui;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Toolkit;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;

import org.apache.commons.math3.stat.StatUtils;

import pl.poznan.put.cs.bioserver.beans.ComparisonLocal;
import pl.poznan.put.cs.bioserver.beans.ComparisonLocalMulti;
import pl.poznan.put.cs.bioserver.torsion.AngleAverageAll;

public class DialogColorbar extends JDialog {
    private static final long serialVersionUID = 2659329749184089277L;

    public DialogColorbar(final ComparisonLocalMulti localMulti) {
        super();

        setLayout(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = 0;

        final List<Colorbar> list = new ArrayList<>();
        final List<ComparisonLocal> results = localMulti.getResults();
        for (ComparisonLocal local : results) {
            c.gridx = 0;
            c.weightx = 1;
            c.weighty = 1;
            c.fill = GridBagConstraints.BOTH;
            Colorbar colorbar = new Colorbar(local);
            list.add(colorbar);
            add(colorbar, c);
            c.gridx++;
            c.weightx = 0;
            c.weighty = 0;
            c.fill = GridBagConstraints.NONE;
            add(new JLabel(local.getTitle()), c);
            c.gridy++;
        }

        ColorbarTicks colorbarTicks =
                new ColorbarTicks(localMulti.getReferenceSequence());
        c.gridx = 0;
        c.weightx = 1;
        c.weighty = 0;
        c.fill = GridBagConstraints.BOTH;
        add(colorbarTicks, c);

        final JCheckBox checkRelative = new JCheckBox("Relative");
        c.gridx++;
        c.weightx = 0;
        c.weighty = 0;
        c.fill = GridBagConstraints.NONE;
        add(checkRelative, c);

        setTitle("Colorbar");

        Toolkit toolkit = Toolkit.getDefaultToolkit();
        Dimension size = toolkit.getScreenSize();
        setSize(size.width * 2 / 3, size.height * 2 / 3);
        setLocation(size.width / 6, size.height / 6);

        checkRelative.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent arg0) {
                double min = 0;
                double max = Math.PI;

                if (checkRelative.isSelected()) {
                    double lmin = Math.PI;
                    double lmax = 0;
                    for (ComparisonLocal local : results) {
                        double[] deltas =
                                local.getAngles()
                                        .get(AngleAverageAll.getInstance()
                                                .getAngleName()).getDeltas();
                        lmin = Math.min(lmin, StatUtils.min(deltas));
                        lmax = Math.max(lmax, StatUtils.max(deltas));
                    }
                    min = lmin;
                    max = lmax;
                }

                for (Colorbar colorbar : list) {
                    colorbar.setMinMax(min, max);
                }
                repaint();
            }
        });
    }
}
