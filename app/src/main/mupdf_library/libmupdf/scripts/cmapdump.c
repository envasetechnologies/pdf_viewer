/* cmapdump.c -- parse a CMap file and dump it as a c-struct */

/* We never want to build memento versions of the cmapdump util */
#undef MEMENTO

/* We never want large file access here */
#undef FZ_LARGEFILE

#include <stdio.h>
#include <string.h>

#include "mupdf/fitz.h"
#include "mupdf/pdf.h"

#include "../source/fitz/context.c"
#include "../source/fitz/error.c"
#include "../source/fitz/memory.c"
#include "../source/fitz/output.c"
#include "../source/fitz/string.c"
#include "../source/fitz/buffer.c"
#include "../source/fitz/crypt-md5.c"
#include "../source/fitz/stream-open.c"
#include "../source/fitz/stream-read.c"
#include "../source/fitz/strtod.c"
#include "../source/fitz/strtof.c"
#include "../source/fitz/ftoa.c"
#include "../source/fitz/printf.c"
#ifdef _WIN32
#include "../source/fitz/time.c"
#endif

#include "../source/pdf/pdf-lex.c"
#include "../source/pdf/pdf-cmap.c"
#include "../source/pdf/pdf-cmap-parse.c"

static void
clean(char *p)
{
	while (*p)
	{
		if ((*p == '/') || (*p == '.') || (*p == '\\') || (*p == '-'))
			*p = '_';
		p ++;
	}
}

int
main(int argc, char **argv)
{
	pdf_cmap *cmap;
	fz_stream *fi;
	FILE *fo;
	char name[256];
	char *realname;
	int i, k;
	fz_context *ctx;

	if (argc < 3)
	{
		fprintf(stderr, "usage: cmapdump output.c lots of cmap files\n");
		return 1;
	}

	ctx = fz_new_context(NULL, NULL, FZ_STORE_UNLIMITED);
	if (!ctx)
	{
		fprintf(stderr, "cannot initialise context\n");
		return 1;
	}

#undef fopen
	fo = fopen(argv[1], "wb");
	if (!fo)
	{
		fprintf(stderr, "cmapdump: could not open output file '%s'\n", argv[1]);
		return 1;
	}

	fprintf(fo, "/* This is an automatically generated file. Do not edit. */\n\n");
	fprintf(fo, "#include \"mupdf/fitz.h\"\n");
	fprintf(fo, "#include \"mupdf/pdf.h\"\n");

	for (i = 2; i < argc; i++)
	{
		realname = strrchr(argv[i], '/');
		if (!realname)
			realname = strrchr(argv[i], '\\');
		if (realname)
			realname ++;
		else
			realname = argv[i];

		/* ignore VCS folders (such as .svn) */
		if (*realname == '.')
			continue;

		if (strlen(realname) > (sizeof name - 1))
		{
			fprintf(stderr, "cmapdump: file name too long\n");
			if (fclose(fo))
			{
				fprintf(stderr, "cmapdump: could not close output file '%s'\n", argv[1]);
				return 1;
			}
			return 1;
		}

		strcpy(name, realname);
		clean(name);

		fi = fz_open_file(ctx, argv[i]);
		cmap = pdf_load_cmap(ctx, fi);
		fz_drop_stream(ctx, fi);

		fprintf(fo, "\n/* %s */\n\n", cmap->cmap_name);

		if (cmap->rlen)
		{
			fprintf(fo, "static const pdf_range cmap_%s_ranges[] = {", name);
			for (k = 0; k < cmap->rlen; k++)
			{
				if (k % 4 == 0)
					fprintf(fo, "\n");
				fprintf(fo, "{%uu,%uu,%uu},", cmap->ranges[k].low, cmap->ranges[k].high, cmap->ranges[k].out);
			}
			fprintf(fo, "\n};\n\n");
		}

		if (cmap->xlen)
		{
			fprintf(fo, "static const pdf_xrange cmap_%s_xranges[] = {", name);
			for (k = 0; k < cmap->xlen; k++)
			{
				if (k % 4 == 0)
					fprintf(fo, "\n");
				fprintf(fo, "{%uu,%uu,%uu},", cmap->xranges[k].low, cmap->xranges[k].high, cmap->xranges[k].out);
			}
			fprintf(fo, "\n};\n\n");
		}

		if (cmap->mlen > 0)
		{
			fprintf(fo, "static const pdf_mrange cmap_%s_mranges[] = {", name);
			for (k = 0; k < cmap->mlen; k++)
			{
				fprintf(fo, "\n{%uu,%uu},", cmap->mranges[k].low, cmap->mranges[k].out);
			}
			fprintf(fo, "\n};\n\n");
		}

		if (cmap->dlen > 0)
		{
			fprintf(fo, "static const int cmap_%s_dict[] = {", name);
			for (k = 0; k < cmap->dlen; k++)
			{
				if (k % 4 == 0)
					fprintf(fo, "\n");
				fprintf(fo, "%uu,", cmap->dict[k]);
			}
			fprintf(fo, "\n};\n\n");
		}

		fprintf(fo, "pdf_cmap pdf_cmap_%s = {\n", name);
		fprintf(fo, "\t{-1, pdf_drop_cmap_imp}, ");
		fprintf(fo, "\"%s\", ", cmap->cmap_name);
		fprintf(fo, "\"%s\", 0, ", cmap->usecmap_name);
		fprintf(fo, "%u, ", cmap->wmode);
		fprintf(fo, "%u,\n\t{ ", cmap->codespace_len);
		if (cmap->codespace_len == 0)
		{
			fprintf(fo, "{0,0,0},");
		}
		for (k = 0; k < cmap->codespace_len; k++)
		{
			fprintf(fo, "{%u,%uu,%uu},", cmap->codespace[k].n, cmap->codespace[k].low, cmap->codespace[k].high);
		}
		fprintf(fo, " },\n");

		if (cmap->rlen)
			fprintf(fo, "\t%u, %u, (pdf_range*) cmap_%s_ranges,\n", cmap->rlen, cmap->rlen, name);
		else
			fprintf(fo, "\t0, 0, NULL,\n");
		if (cmap->xlen)
			fprintf(fo, "\t%u, %u, (pdf_xrange*) cmap_%s_xranges,\n", cmap->xlen, cmap->xlen, name);
		else
			fprintf(fo, "\t0, 0, NULL,\n");
		if (cmap->mlen)
			fprintf(fo, "\t%u, %u, (pdf_mrange*) cmap_%s_mranges,\n", cmap->mlen, cmap->mlen, name);
		else
			fprintf(fo, "\t0, 0, NULL,\n");
		if (cmap->dict)
			fprintf(fo, "\t%u, %u, (int*) cmap_%s_dict,\n", cmap->dlen, cmap->dlen, name);
		else
			fprintf(fo, "\t0, 0, NULL,\n");

		fprintf(fo, "};\n");

		if (getenv("verbose"))
			printf("\t{\"%s\",&cmap_%s},\n", cmap->cmap_name, name);

		pdf_drop_cmap(ctx, cmap);
	}

	if (fclose(fo))
	{
		fprintf(stderr, "cmapdump: could not close output file '%s'\n", argv[1]);
		return 1;
	}

	fz_drop_context(ctx);
	return 0;
}

void fz_new_font_context(fz_context *ctx)
{
}

void fz_drop_font_context(fz_context *ctx)
{
}

fz_font_context *fz_keep_font_context(fz_context *ctx)
{
	return NULL;
}

void fz_new_colorspace_context(fz_context *ctx)
{
}

void fz_drop_colorspace_context(fz_context *ctx)
{
}

fz_colorspace_context *fz_keep_colorspace_context(fz_context *ctx)
{
	return NULL;
}

void fz_new_aa_context(fz_context *ctx)
{
}

void fz_drop_aa_context(fz_context *ctx)
{
}

void fz_copy_aa_context(fz_context *dst, fz_context *src)
{
}

void *fz_keep_storable(fz_context *ctx, const fz_storable *sc)
{
	fz_storable *s = (fz_storable *)sc;
	return fz_keep_imp(ctx, s, &s->refs);
}

void fz_drop_storable(fz_context *ctx, const fz_storable *sc)
{
	fz_storable *s = (fz_storable *)sc;
	if (fz_drop_imp(ctx, s, &s->refs))
		s->drop(ctx, s);
}

void fz_new_store_context(fz_context *ctx, size_t max)
{
}

void fz_drop_store_context(fz_context *ctx)
{
}

fz_store *fz_keep_store_context(fz_context *ctx)
{
	return NULL;
}

int fz_store_scavenge(fz_context *ctx, size_t size, int *phase)
{
	return 0;
}

void fz_new_glyph_cache_context(fz_context *ctx)
{
}

void fz_drop_glyph_cache_context(fz_context *ctx)
{
}

fz_glyph_cache *fz_keep_glyph_cache(fz_context *ctx)
{
	return NULL;
}

void fz_new_document_handler_context(fz_context *ctx)
{
}

void fz_drop_document_handler_context(fz_context *ctx)
{
}

fz_document_handler_context *fz_keep_document_handler_context(fz_context *ctx)
{
	return NULL;
}

void fz_default_image_decode(void *arg, int w, int h, int l2factor, fz_irect *irect)
{
}

int fz_default_image_scale(void *arg, int w, int h, int src_w, int src_h)
{
	return 0;
}
