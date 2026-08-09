package com.ragedriver.sodiumvolt.client.inspector;

import java.util.List;

/** Bounded reusable HUD layout. Text measurement happens only on a key change. */
final class InspectorHudLayoutCache {
	interface TextMetrics {
		int width(String value);

		String truncateOverflow(String value, int maximumWidth);
	}

	private final String[] renderedLines;
	private List<String> sourceLines;
	private Object fontIdentity;
	private int guiWidth;
	private int guiHeight;
	private int visibleLineCount;
	private int left;
	private int right;
	private int top;
	private int bottom;

	InspectorHudLayoutCache(int maximumLines) {
		if (maximumLines < 1) {
			throw new IllegalArgumentException("maximumLines must be positive");
		}
		this.renderedLines = new String[maximumLines];
	}

	boolean prepare(
			List<String> lines,
			Object currentFontIdentity,
			int currentGuiWidth,
			int currentGuiHeight,
			TextMetrics metrics
	) {
		if (this.sourceLines == lines
				&& this.fontIdentity == currentFontIdentity
				&& this.guiWidth == currentGuiWidth
				&& this.guiHeight == currentGuiHeight) {
			return false;
		}
		clearRenderedLines();
		this.sourceLines = lines;
		this.fontIdentity = currentFontIdentity;
		this.guiWidth = currentGuiWidth;
		this.guiHeight = currentGuiHeight;
		this.left = 0;
		this.right = 0;
		this.top = 0;
		this.bottom = 0;

		int availableTextWidth = currentGuiWidth <= 16 ? 0 : currentGuiWidth - 16;
		int availablePanelHeight = currentGuiHeight <= 12 ? 0 : currentGuiHeight - 12;
		this.visibleLineCount = Math.min(
				Math.min(lines.size(), this.renderedLines.length),
				availablePanelHeight / 10
		);
		if (this.visibleLineCount == 0 || availableTextWidth == 0) {
			this.visibleLineCount = 0;
			return true;
		}

		int width = 0;
		for (int index = 0; index < this.visibleLineCount; index++) {
			String line = lines.get(index);
			int measuredWidth = Math.max(0, metrics.width(line));
			if (measuredWidth > availableTextWidth) {
				this.renderedLines[index] = metrics.truncateOverflow(line, availableTextWidth);
				width = availableTextWidth;
			} else {
				this.renderedLines[index] = line;
				width = Math.max(width, measuredWidth);
			}
		}
		this.left = Math.max(4, currentGuiWidth - width - 12);
		this.right = currentGuiWidth - 4;
		this.top = 4;
		this.bottom = this.top + this.visibleLineCount * 10 + 8;
		return true;
	}

	void invalidate() {
		this.sourceLines = null;
		this.fontIdentity = null;
		this.guiWidth = 0;
		this.guiHeight = 0;
		this.visibleLineCount = 0;
		this.left = 0;
		this.right = 0;
		this.top = 0;
		this.bottom = 0;
		clearRenderedLines();
	}

	int visibleLineCount() {
		return this.visibleLineCount;
	}

	String lineAt(int index) {
		return this.renderedLines[index];
	}

	int left() {
		return this.left;
	}

	int right() {
		return this.right;
	}

	int top() {
		return this.top;
	}

	int bottom() {
		return this.bottom;
	}

	boolean retainsLineForTesting(String line) {
		if (this.sourceLines != null) {
			for (String source : this.sourceLines) {
				if (source == line) {
					return true;
				}
			}
		}
		for (String rendered : this.renderedLines) {
			if (rendered == line) {
				return true;
			}
		}
		return false;
	}

	private void clearRenderedLines() {
		for (int index = 0; index < this.renderedLines.length; index++) {
			this.renderedLines[index] = null;
		}
	}
}
