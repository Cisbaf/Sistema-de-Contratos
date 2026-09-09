"use client";

import "@mdxeditor/editor/style.css";
import {
  BoldItalicUnderlineToggles,
  headingsPlugin,
  InsertTable,
  linkPlugin,
  listsPlugin,
  ListsToggle,
  MDXEditor,
  quotePlugin,
  tablePlugin,
  thematicBreakPlugin,
  toolbarPlugin,
  UndoRedo,
} from "@mdxeditor/editor";

// Criado uma única vez fora do componente: o MDXEditor exige que o array de
// plugins tenha referência estável entre renderizações, senão reinicializa
// e perde sincronia com o onChange a cada tecla digitada.
const plugins = [
  headingsPlugin(),
  listsPlugin(),
  quotePlugin(),
  thematicBreakPlugin(),
  linkPlugin(),
  tablePlugin(),
  toolbarPlugin({
    toolbarContents: () => (
      <>
        <UndoRedo />
        <BoldItalicUnderlineToggles options={["Bold", "Italic"]} />
        <ListsToggle />
        <InsertTable />
      </>
    ),
  }),
];

export default function MarkdownEditor({ value, onChange }: { value: string; onChange: (value: string) => void }) {
  return (
    <MDXEditor
      markdown={value}
      onChange={onChange}
      contentEditableClassName="template-markdown-editor"
      suppressHtmlProcessing
      plugins={plugins}
    />
  );
}
