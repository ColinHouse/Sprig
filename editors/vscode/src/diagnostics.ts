import { SourceRange } from './compiler';

/** ANTLR positions count Unicode code points; VS Code uses UTF-16 code units. */
export function mapRange(range: SourceRange, text: string): SourceRange {
  const lines = text.split(/\r\n|\n|\r/);
  const convert = (point: {line:number; character:number}) => {
    const line = Math.max(0, Math.min(point.line, lines.length-1));
    const content = lines[line];
    const character = [...content].slice(0,Math.max(0,point.character)).join('').length;
    return {line,character};
  };
  return {start:convert(range.start),end:convert(range.end)};
}
