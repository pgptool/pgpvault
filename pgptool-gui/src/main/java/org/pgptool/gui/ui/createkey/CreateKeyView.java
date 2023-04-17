/*******************************************************************************
 * PGPTool is a desktop application for pgp encryption/decryption
 * Copyright (C) 2019 Sergey Karpushin
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 ******************************************************************************/
package org.pgptool.gui.ui.createkey;

import java.awt.BorderLayout;
import java.awt.Dialog.ModalityType;
import java.awt.FlowLayout;
import java.awt.Window;
import java.awt.event.ActionEvent;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JProgressBar;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

import org.pgptool.gui.app.Messages;
import org.pgptool.gui.ui.swingpm.DialogViewBaseEx;
import org.pgptool.gui.ui.tools.ControlsDisabler;
import org.pgptool.gui.ui.tools.UiUtils;

import ru.skarpushin.swingpm.bindings.TypedPropertyChangeListener;
import ru.skarpushin.swingpm.tools.sglayout.SgLayout;

public class CreateKeyView extends DialogViewBaseEx<CreateKeyPm> {
	private JPanel pnl;

	private JTextField fullName;
	private JTextField email;
	private JPasswordField passphrase;
	private JPasswordField passphraseAgain;
	private JProgressBar progressBar;
	private TypedPropertyChangeListener<Boolean> isDisableControlsChanged;

	private JButton btnCreate;
	private JButton btnCancel;

	@Override
	protected void internalInitComponents() {
		pnl = new JPanel(new BorderLayout());

		JPanel pnlControls;
		pnl.add(pnlControls = buildPanelKeyInfo(), BorderLayout.CENTER);
		pnl.add(buildPanelButtons(), BorderLayout.SOUTH);

		isDisableControlsChanged = new ControlsDisabler(pnlControls);
	}

	private JPanel buildPanelKeyInfo() {
		SgLayout sgl = new SgLayout(2, 4, spacing(1), 2);
		sgl.setColSize(0, 1, SgLayout.SIZE_TYPE_ASKCOMPONENT);
		sgl.setColSize(1, spacing(25), SgLayout.SIZE_TYPE_CONSTANT);

		JPanel ret = new JPanel(sgl);
		ret.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));

		int row = 0;
		ret.add(lbl("term.fullName"), sgl.cs(0, row));
		ret.add(fullName = new JTextField(), sgl.cs(1, row));
		row++;
		ret.add(lbl("term.email"), sgl.cs(0, row));
		ret.add(email = new JTextField(), sgl.cs(1, row));
		row++;
		ret.add(lbl("term.passphrase"), sgl.cs(0, row));
		ret.add(passphrase = new JPasswordField(), sgl.cs(1, row));
		row++;
		ret.add(lbl("term.passphraseAgain"), sgl.cs(0, row));
		ret.add(passphraseAgain = new JPasswordField(), sgl.cs(1, row));

		return ret;
	}

	private JLabel lbl(String code) {
		return new JLabel(Messages.get(code), SwingConstants.RIGHT);
	}

	private JPanel buildPanelButtons() {
		JPanel whole = new JPanel(new BorderLayout());

		progressBar = new JProgressBar(0, 100);
		progressBar.setToolTipText(Messages.text("phrase.generatingYourSecureKey"));
		progressBar.setIndeterminate(true);

		JPanel btns = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
		btns.setBorder(BorderFactory.createEmptyBorder(5, 10, 10, 10));
		btns.add(progressBar);
		btns.add(new JLabel("  "));
		btns.add(btnCreate = new JButton());
		btns.add(btnCancel = new JButton());
		whole.add(btns, BorderLayout.EAST);

		return whole;
	}

	@Override
	protected void internalBindToPm() {
		super.internalBindToPm();

		bindingContext.setupBinding(pm.getFullName(), fullName);
		bindingContext.setupBinding(pm.getEmail(), email);
		bindingContext.setupBinding(pm.getPassphrase(), passphrase);
		bindingContext.setupBinding(pm.getPassphraseAgain(), passphraseAgain);

		bindingContext.setupBinding(pm.actionCreate, btnCreate);
		bindingContext.setupBinding(pm.actionCancel, btnCancel);

		bindingContext.registerPropertyValuePropagation(pm.getProgressBarVisible(), progressBar, "visible");
		bindingContext.registerOnChangeHandler(pm.getIsDisableControls(), isDisableControlsChanged);
		isDisableControlsChanged.handlePropertyChanged(this, null, false, pm.getIsDisableControls().getValue());
	}

	@Override
	protected JDialog initDialog(Window owner, Object constraints) {
		JDialog ret = new JDialog(owner, ModalityType.APPLICATION_MODAL);
		ret.setLayout(new BorderLayout());
		ret.setResizable(false);
		ret.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
		ret.setTitle(Messages.get("action.createPgpKey"));
		ret.add(pnl, BorderLayout.CENTER);
		ret.pack();
		UiUtils.centerWindow(ret, owner);
		return ret;
	}

	@Override
	protected JPanel getRootPanel() {
		return pnl;
	}

	@Override
	protected void handleDialogShown() {
		super.handleDialogShown();
		dialog.getRootPane().setDefaultButton(btnCreate);
		fullName.requestFocusInWindow();
	}

	@Override
	protected void dispatchWindowCloseEvent(ActionEvent originAction) {
		btnCancel.getAction().actionPerformed(originAction);
	}

}
