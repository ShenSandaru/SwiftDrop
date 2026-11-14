import { Fragment, type ReactNode } from "react";
import Link from "next/link";

/** SwiftDrop tracking numbers: SD followed by 10 letters or digits. */
const TRACKING_NUMBER = /\b(SD[A-Z0-9]{10})\b/g;
/** **bold** or *italic*; underscores are left alone because tool names contain them. */
const EMPHASIS = /\*\*(.+?)\*\*|\*(?=\S)(.+?)(?<=\S)\*/g;

/**
 * Renders the small amount of Markdown the model may use (paragraphs, bullet and numbered lists,
 * **bold**, *italic*, # headings shown as bold lines) as React elements, never as HTML, and turns
 * tracking numbers into tracking links.
 */
export function RichText({ text }: { text: string }) {
  const blocks = text
    .trim()
    .replace(/^#{1,6}\s+(.+)$/gm, "**$1**")
    .split(/\n{2,}/);
  return (
    <div className="space-y-2">
      {blocks.map((block, index) => {
        const lines = block.split("\n");
        const bullets = lines.every((line) => /^\s*([-*•]|\d+\.)\s+/.test(line));
        if (bullets) {
          const ordered = /^\s*\d+\./.test(lines[0]);
          const List = ordered ? "ol" : "ul";
          return (
            <List key={index} className={ordered ? "list-decimal space-y-1 pl-5" : "list-disc space-y-1 pl-5"}>
              {lines.map((line, item) => (
                <li key={item}>{inline(line.replace(/^\s*([-*•]|\d+\.)\s+/, ""))}</li>
              ))}
            </List>
          );
        }
        return (
          <p key={index}>
            {lines.map((line, item) => (
              <Fragment key={item}>
                {item > 0 && <br />}
                {inline(line)}
              </Fragment>
            ))}
          </p>
        );
      })}
    </div>
  );
}

function inline(text: string): ReactNode[] {
  const parts: ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(EMPHASIS)) {
    parts.push(...links(text.slice(last, match.index), parts.length));
    parts.push(match[1] !== undefined
      ? <strong key={`b${parts.length}`} className="font-bold">{links(match[1], parts.length)}</strong>
      : <em key={`i${parts.length}`} className="italic text-muted-foreground">{links(match[2], parts.length)}</em>);
    last = (match.index ?? 0) + match[0].length;
  }
  parts.push(...links(text.slice(last), parts.length));
  return parts;
}

function links(text: string, offset: number): ReactNode[] {
  const parts: ReactNode[] = [];
  let last = 0;
  for (const match of text.matchAll(TRACKING_NUMBER)) {
    parts.push(text.slice(last, match.index));
    parts.push(
      <Link key={`t${offset}-${parts.length}`} href={`/track?code=${match[1]}`}
        className="font-mono font-semibold text-primary underline decoration-primary/40 underline-offset-2 hover:decoration-primary">
        {match[1]}
      </Link>,
    );
    last = (match.index ?? 0) + match[0].length;
  }
  parts.push(text.slice(last));
  return parts;
}
