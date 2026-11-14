package radar.connector;

import java.util.Set;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.jsoup.select.NodeTraversor;
import org.jsoup.select.NodeVisitor;

// offer descriptions are stored as plain text: searchable as is and no HTML reaches Claude
public final class HtmlText {

  private static final Pattern TAG = Pattern.compile("</?[a-zA-Z][^>]*>");
  private static final Pattern BLANK_LINES = Pattern.compile("\n{3,}");
  private static final Set<String> BLOCKS = Set.of("p", "div", "ul", "ol",
      "h1", "h2", "h3", "h4", "h5", "h6", "table", "tr");

  private HtmlText() {
  }

  public static boolean isHtml(String text) {
    return text != null && TAG.matcher(text).find();
  }

  // keeps the structure readable: paragraphs split by a blank line, list items as "- item" lines
  public static String toText(String html) {
    if (!isHtml(html)) {
      return html;
    }

    var out = new StringBuilder();
    NodeTraversor.traverse(new NodeVisitor() {
      @Override
      public void head(Node node, int depth) {
        if (node instanceof TextNode text) {
          out.append(text.text());
        } else if (node instanceof Element element) {
          switch (element.normalName()) {
            case "li" -> out.append("\n- ");
            case "br" -> out.append('\n');
            default -> {
              breakBlock(element, out);
              breakInsideItem(element, out);
            }
          }
        }
      }

      @Override
      public void tail(Node node, int depth) {
        if (node instanceof Element element) {
          breakBlock(element, out);
        }
      }
    }, Jsoup.parseBodyFragment(html).body());

    return tidy(out.toString());
  }

  private static void breakBlock(Element element, StringBuilder out) {
    if (BLOCKS.contains(element.normalName()) && element.closest("li") == null) {
      out.append("\n\n");
    }
  }

  // a list item's first paragraph stays on the item's line, the next ones go below it
  private static void breakInsideItem(Element element, StringBuilder out) {
    if (BLOCKS.contains(element.normalName()) && element.closest("li") != null
        && element.previousElementSibling() != null) {
      out.append('\n');
    }
  }

  private static String tidy(String text) {
    var lines = text.lines()
        .map(String::strip)
        .filter(line -> !line.equals("-"))
        .toList();

    return BLANK_LINES.matcher(String.join("\n", lines)).replaceAll("\n\n").strip();
  }
}
