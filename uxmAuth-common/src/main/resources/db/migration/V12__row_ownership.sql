-- Which website row a link is bound to. row_mark is a SHA-256, in hex, of what tells the row the link was made
-- for from a row made later under the same name: its registration date, or its number when it has no date, or its
-- password when it has neither. A row whose mark differs was made again, by whoever registered the name on the
-- website after the first one went, and is not the account's. A link made before this column has no mark, and
-- takes the mark of the row it finds first.
ALTER TABLE uxmauth_website_link ADD COLUMN row_mark VARCHAR(64);
