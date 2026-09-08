"use client";

import { useEffect, useState } from "react";

type Props = {
  value: string;
  onChange: (value: string) => void;
};

export function RichTextEditor({ value, onChange }: Props) {
  const [Editor, setEditor] = useState<null | React.ComponentType<Props>>(null);

  useEffect(() => {
    let cancelled = false;
    (async () => {
      const [{ CKEditor }, { ClassicEditor, Essentials, Bold, Italic, Paragraph, Heading, List, Link, BlockQuote }] =
        await Promise.all([import("@ckeditor/ckeditor5-react"), import("ckeditor5")]);
      await import("ckeditor5/ckeditor5.css");

      function BoundEditor({ value: html, onChange: setHtml }: Props) {
        return (
          <div className="ck-admin-editor rounded-xl border border-border bg-surface">
            <CKEditor
              editor={ClassicEditor}
              data={html}
              config={{
                licenseKey: "GPL",
                plugins: [Essentials, Bold, Italic, Paragraph, Heading, List, Link, BlockQuote],
                toolbar: ["undo", "redo", "|", "heading", "|", "bold", "italic", "|", "bulletedList", "numberedList", "|", "link", "blockQuote"],
              }}
              onChange={(_event, editor) => {
                setHtml(editor.getData());
              }}
            />
          </div>
        );
      }

      if (!cancelled) setEditor(() => BoundEditor);
    })().catch((err) => {
      console.error("CKEditor load failed", err);
    });
    return () => {
      cancelled = true;
    };
  }, []);

  if (!Editor) {
    return (
      <textarea
        className="min-h-40 rounded-xl border border-border bg-surface px-3 py-2 text-sm"
        value={value}
        onChange={(e) => onChange(e.target.value)}
        placeholder="상세 설명을 입력하세요"
      />
    );
  }

  return <Editor value={value} onChange={onChange} />;
}
