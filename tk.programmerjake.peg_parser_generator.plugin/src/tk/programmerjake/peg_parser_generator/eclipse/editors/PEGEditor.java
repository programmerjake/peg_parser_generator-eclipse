package tk.programmerjake.peg_parser_generator.eclipse.editors;

import org.eclipse.ui.editors.text.TextEditor;

public class PEGEditor extends TextEditor {

	private final ColorManager colorManager = new ColorManager();

	public PEGEditor() {
		super();
		setSourceViewerConfiguration(new PEGConfiguration(colorManager));
		setDocumentProvider(new PEGDocumentProvider());
	}

	public void dispose() {
		colorManager.dispose();
		super.dispose();
	}

}
