package com.materialplanner.ui;

import javax.swing.JList;
import javax.swing.ListModel;

/**
 * A JList that always fits the visible width of its scroll pane. A plain JList widens itself to its
 * widest row, and the scroll pane then clips whatever sits at the right edge (quantities, subtitles).
 */
class ViewportList<E> extends JList<E>
{
	ViewportList(ListModel<E> model)
	{
		super(model);
	}

	@Override
	public boolean getScrollableTracksViewportWidth()
	{
		return true;
	}
}
