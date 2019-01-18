/* -*- mode: java; c-basic-offset: 2; indent-tabs-mode: nil -*- */

/*
  Part of the Processing project - http://processing.org

  Copyright (c) 2012-18 The Processing Foundation
  Copyright (c) 2004-12 Ben Fry and Casey Reas
  Copyright (c) 2001-04 Massachusetts Institute of Technology

  This program is free software; you can redistribute it and/or modify
  it under the terms of the GNU General Public License as published by
  the Free Software Foundation; either version 2 of the License, or
  (at your option) any later version.

  This program is distributed in the hope that it will be useful,
  but WITHOUT ANY WARRANTY; without even the implied warranty of
  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
  GNU General Public License for more details.

  You should have received a copy of the GNU General Public License
  along with this program; if not, write to the Free Software Foundation,
  Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
*/

package processing.app.ui;

import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionAdapter;

import javax.swing.plaf.basic.BasicSplitPaneDivider;
import javax.swing.plaf.basic.BasicSplitPaneUI;

import processing.app.Mode;
import processing.app.Platform;
import processing.app.Preferences;
import processing.core.PApplet;


/**
 * Panel just below the editing area that contains status messages.
 */
public class EditorStatus extends BasicSplitPaneDivider {  //JPanel {
  static final int HIGH = Toolkit.zoom(28);
  static final int LEFT_MARGIN = Editor.LEFT_GUTTER;
  static final int RIGHT_MARGIN = Toolkit.zoom(20);

  Color urlColor;
  Color[] fgColor;
  Color[] bgColor;
  Image[] bgImage;

  @SuppressWarnings("hiding")
  static public final int ERROR   = 1;
  static public final int CURSOR_LINE_ERROR = 2;
  static public final int WARNING = 3;
  static public final int CURSOR_LINE_WARNING = 4;
  static public final int NOTICE  = 0;

  static final int YES    = 1;
  static final int NO     = 2;
  static final int CANCEL = 3;
  static final int OK     = 4;

  Editor editor;

  int mode;
  String message = "";

  String url;
  int rightEdge;
  int mouseX;
  int rolloverState;
  static final int ROLLOVER_NONE     = 0;
  static final int ROLLOVER_URL      = 1;
  static final int ROLLOVER_COLLAPSE = 2;
  static final int ROLLOVER_EMOJI    = 3;

  Font font;
  FontMetrics metrics;
  int ascent;

  // used to draw the clipboard icon
  Font emojiFont;

  Image offscreen;
  int sizeW, sizeH;
  boolean collapseState = false;

  int response;

  boolean indeterminate;
  Thread thread;


  public EditorStatus(BasicSplitPaneUI ui, Editor editor) {
    super(ui);
    this.editor = editor;
    empty();
    updateMode();

    addMouseListener(new MouseAdapter() {

      @Override
      public void mouseEntered(MouseEvent e) {
        updateMouse();
      }

      @Override
      public void mousePressed(MouseEvent e) {
        if (rolloverState == ROLLOVER_URL) {
          Platform.openURL(url);

        } else if (rolloverState == ROLLOVER_EMOJI) {
          if (e.isShiftDown()) {
            // open the text in a browser window as a search
            final String fmt = Preferences.get("search.format");
            Platform.openURL(String.format(fmt, PApplet.urlEncode(message)));

          } else {
            // copy the text to the clipboard
            Clipboard clipboard = getToolkit().getSystemClipboard();
            clipboard.setContents(new StringSelection(message), null);
            System.out.println("Copied to the clipboard. " +
                               "Use shift-click to search the web instead.");
          }

        } else if (rolloverState == ROLLOVER_COLLAPSE) {
          collapse(!collapseState);
        }
      }

      @Override
      public void mouseExited(MouseEvent e) {
        mouseX = -100;
        updateMouse();
      }

    });

    addMouseMotionListener(new MouseMotionAdapter() {
      @Override
      public void mouseDragged(MouseEvent e) {
        // BasicSplitPaneUI.startDragging gets called even when you click but
        // don't drag, so we can't expand the console whenever that gets called
        // or the button wouldn't work.
        collapse(false);
      }

      @Override
      public void mouseMoved(MouseEvent e) {
        mouseX = e.getX();
        updateMouse();
      }
    });
  }


  void collapse(boolean doCollapse) {
    if (collapseState == doCollapse) return;
    collapseState = doCollapse;
    editor.footer.setVisible(!doCollapse);
    splitPane.resetToPreferredSizes();
  }


  void updateMouse() {
    switch (rolloverState) {
    case ROLLOVER_EMOJI:
    case ROLLOVER_URL:
      setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
      break;
    case ROLLOVER_COLLAPSE:
      setCursor(Cursor.getPredefinedCursor(Cursor.DEFAULT_CURSOR));
      break;
    case ROLLOVER_NONE:
      setCursor(Cursor.getPredefinedCursor(Cursor.MOVE_CURSOR));
      break;
    }
    repaint();
  }


  static String findURL(String message) {
    String[] m = PApplet.match(message, "http\\S+");
    if (m != null) {
      return m[0];
    }
    return null;
  }


  public void updateMode() {
    Mode mode = editor.getMode();

    urlColor = mode.getColor("status.url.fgcolor");

    fgColor = new Color[] {
      mode.getColor("status.notice.fgcolor"),
      mode.getColor("status.error.fgcolor"),
      mode.getColor("status.error.fgcolor"),
      mode.getColor("status.warning.fgcolor"),
      mode.getColor("status.warning.fgcolor")
    };

    bgColor = new Color[] {
      mode.getColor("status.notice.bgcolor"),
      mode.getColor("status.error.bgcolor"),
      mode.getColor("status.error.bgcolor"),
      mode.getColor("status.warning.bgcolor"),
      mode.getColor("status.warning.bgcolor")
    };

    bgImage = new Image[] {
      mode.loadImage("/lib/status/notice.png"),
      mode.loadImage("/lib/status/error.png"),
      mode.loadImage("/lib/status/error.png"),
      mode.loadImage("/lib/status/warning.png"),
      mode.loadImage("/lib/status/warning.png")
    };

    font = mode.getFont("status.font");
    //emojiFont = new Font("Dialog", Font.PLAIN, 21);
    emojiFont = mode.getFont("status.emoji.font");
    metrics = null;
  }


  public void empty() {
    mode = NOTICE;
    message = "";
    url = null;
    repaint();
  }


  public void message(String message, int mode) {
    this.message = message;
    this.mode = mode;

    url = findURL(message);
    repaint();
  }


  public void notice(String message) {
    message(message, NOTICE);
//    mode = NOTICE;
//    this.message = message;
//    url = findURL(message);
//    repaint();
  }


//  public void unnotice(String unmessage) {
//    if (message.equals(unmessage)) empty();
//  }


  public void warning(String message) {
    message(message, WARNING);
//    this.message = message;
//    mode = WARNING;
//    url = findURL(message);
//    repaint();
  }


  public void error(String message) {
    message(message, ERROR);
//    this.message = message;
//    mode = ERROR;
//    url = findURL(message);
//    repaint();
  }


  public void startIndeterminate() {
    indeterminate = true;
    thread = new Thread() {
      public void run() {
        while (Thread.currentThread() == thread) {
          repaint();
          try {
            Thread.sleep(1000 / 10);
          } catch (InterruptedException e) { }
        }
      }
    };
    thread.setName("Editor Status");
    thread.start();
  }


  public void stopIndeterminate() {
    indeterminate = false;
    thread = null;
    repaint();
  }


  //public void paintComponent(Graphics screen) {
  public void paint(Graphics screen) {
    Dimension size = getSize();
    if ((size.width != sizeW) || (size.height != sizeH)) {
      // component has been resized
      offscreen = null;
    }

    if (offscreen == null) {
      sizeW = size.width;
      sizeH = size.height;

      offscreen = Toolkit.offscreenGraphics(this, sizeW, sizeH);
    }

    Graphics g = offscreen.getGraphics();
    /*Graphics2D g2 =*/ Toolkit.prepareGraphics(g);

    g.setFont(font);
    if (metrics == null) {
      metrics = g.getFontMetrics();
      ascent = metrics.getAscent();
    }

    g.drawImage(bgImage[mode], 0, 0, sizeW, sizeH, this);

    // What's the mouse over?
    if (sizeW - sizeH < mouseX && mouseX < sizeW) {
      rolloverState = ROLLOVER_COLLAPSE;
    } else if (message != null && !message.isEmpty()) {
      if (sizeW - 2*sizeH < mouseX) {
        rolloverState = ROLLOVER_EMOJI;
      } else if (url != null && mouseX > LEFT_MARGIN &&
        // calculate right edge of the text for rollovers (otherwise the pane
        // cannot be resized up or down whenever a URL is being displayed)
        mouseX < (LEFT_MARGIN + g.getFontMetrics().stringWidth(message))) {
        rolloverState = ROLLOVER_URL;
      } else {
        rolloverState = ROLLOVER_NONE;
      }
    } else {
      rolloverState = ROLLOVER_NONE;
    }

    // https://github.com/processing/processing/issues/3265
    if (message != null) {
      // needs to be set each time on osx
      g.setFont(font);
      // set the highlight color on rollover so that the user's not surprised
      // to see the web browser open when they click
      g.setColor((rolloverState == ROLLOVER_URL) ? urlColor : fgColor[mode]);
      g.drawString(message, LEFT_MARGIN, (sizeH + ascent) / 2);
    }

    if (indeterminate) {
      //int x = cancelButton.getX();
      //int w = cancelButton.getWidth();
      int w = Toolkit.getButtonWidth();
      int x = getWidth() - Math.max(RIGHT_MARGIN, (int)(sizeH*1.2)) - w;
      int y = sizeH / 3;
      int h = sizeH / 3;
      g.setColor(new Color(0x80000000, true));
      g.drawRect(x, y, w, h);
      for (int i = 0; i < 10; i++) {
        int r = (int) (x + Math.random() * w);
        g.drawLine(r, y, r, y+h);
      }

    } else if (!message.isEmpty()) {
      g.setFont(emojiFont);
      // actual Clipboard character not available [fry 180326]
      //g.drawString("\uD83D\uDCCB", sizeW - LEFT_MARGIN, (sizeH + ascent) / 2);
      // other apps seem to use this one as a hack: ⎘
      drawButton(g, "\u2398", 1, rolloverState == ROLLOVER_EMOJI);
      g.setFont(font);
    }

    // draw collapse/expand button
    drawButton(g, collapseState ? "▲" : "▼", 0, rolloverState == ROLLOVER_COLLAPSE);

    screen.drawImage(offscreen, 0, 0, sizeW, sizeH, null);
  }


  private final Color whitishTint = new Color(0x40eeeeee, true);
  /**
   * @param pos A zero-based index with 0 on the right.
   */
  private void drawButton(Graphics g, String symbol, int pos, boolean highlight) {
    int left = sizeW - (pos + 1) * sizeH;
    g.setColor(bgColor[mode]);  // Overlap very long errors.
    g.fillRect(left, 0, sizeH, sizeH);
    if (highlight) {
      g.setColor(whitishTint);
      g.fillRect(left, 0, sizeH, sizeH);
      g.setColor(urlColor);
    } else {
      g.setColor(fgColor[mode]);
    }
    g.drawString(symbol,
        left + (sizeH - g.getFontMetrics().stringWidth(symbol))/2,
        (sizeH + ascent) / 2);
  }

  public Dimension getPreferredSize() {
    return getMinimumSize();
  }


  public Dimension getMinimumSize() {
    return new Dimension(Toolkit.zoom(300), HIGH);
  }


  public Dimension getMaximumSize() {
    return new Dimension(super.getMaximumSize().width, HIGH);
  }
}
