/*
 * Copyright (C) 2026 HoDoKu contributors
 *
 * This file is part of HoDoKu and is licensed under GPL-3.0-or-later.
 */
package sudoku;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Parses the compact cell and unit notation used by {@link SolutionStep}. */
public final class SudokuReferenceParser {

	private static final Pattern CELL_PATTERN = Pattern.compile(
			"(?i)(?<![a-z0-9])(?:ef|f)?r([1-9]{1,9})c([1-9]{1,9})(?![a-z0-9])");
	private static final Pattern UNIT_SEQUENCE_PATTERN = Pattern.compile(
			"(?i)(?<![a-z0-9])(?:[rcb][1-9]{1,9})+(?![a-z0-9])");
	private static final Pattern UNIT_SEGMENT_PATTERN = Pattern.compile("(?i)([rcb])([1-9]{1,9})");
	private static final Pattern CHINESE_BLOCK_PATTERN = Pattern.compile(
			"(?:第)?([1-9一二三四五六七八九])(?:个)?宫");

	private SudokuReferenceParser() {
	}

	public static List<SudokuTextReference> parse(String text) {
		if (text == null || text.isEmpty()) {
			return Collections.emptyList();
		}

		List<SudokuTextReference> result = new ArrayList<SudokuTextReference>();
		boolean[] occupied = new boolean[text.length()];
		Matcher matcher = CELL_PATTERN.matcher(text);
		while (matcher.find()) {
			String rows = matcher.group(1);
			String columns = matcher.group(2);
			if (rows.length() > 1 && columns.length() > 1) {
				continue;
			}
			addReference(result, occupied, matcher.start(), matcher.end(), text,
					SudokuTextReference.Kind.CELLS, cells(rows, columns));
		}

		matcher = UNIT_SEQUENCE_PATTERN.matcher(text);
		while (matcher.find()) {
			if (overlaps(occupied, matcher.start(), matcher.end())) {
				continue;
			}
			Matcher segment = UNIT_SEGMENT_PATTERN.matcher(matcher.group());
			while (segment.find()) {
				int start = matcher.start() + segment.start();
				int end = matcher.start() + segment.end();
				char type = Character.toLowerCase(segment.group(1).charAt(0));
				String numbers = segment.group(2);
				if (type == 'r') {
					addReference(result, occupied, start, end, text, SudokuTextReference.Kind.ROWS,
							rows(numbers));
				} else if (type == 'c') {
					addReference(result, occupied, start, end, text, SudokuTextReference.Kind.COLUMNS,
							columns(numbers));
				} else {
					addReference(result, occupied, start, end, text, SudokuTextReference.Kind.BLOCKS,
							blocks(numbers));
				}
			}
		}

		matcher = CHINESE_BLOCK_PATTERN.matcher(text);
		while (matcher.find()) {
			if (!overlaps(occupied, matcher.start(), matcher.end())) {
				addReference(result, occupied, matcher.start(), matcher.end(), text,
						SudokuTextReference.Kind.BLOCKS, block(chineseNumber(matcher.group(1).charAt(0))));
			}
		}

		Collections.sort(result, new Comparator<SudokuTextReference>() {
			@Override
			public int compare(SudokuTextReference first, SudokuTextReference second) {
				return first.getStart() - second.getStart();
			}
		});
		return Collections.unmodifiableList(result);
	}

	private static void addReference(List<SudokuTextReference> result, boolean[] occupied, int start, int end,
			String source, SudokuTextReference.Kind kind, int[] cells) {
		if (cells.length == 0 || overlaps(occupied, start, end)) {
			return;
		}
		result.add(new SudokuTextReference(start, end, source.substring(start, end), kind, cells));
		for (int i = start; i < end; i++) {
			occupied[i] = true;
		}
	}

	private static boolean overlaps(boolean[] occupied, int start, int end) {
		for (int i = start; i < end; i++) {
			if (occupied[i]) {
				return true;
			}
		}
		return false;
	}

	private static int[] cells(String rows, String columns) {
		Set<Integer> result = new LinkedHashSet<Integer>();
		for (int i = 0; i < rows.length(); i++) {
			for (int j = 0; j < columns.length(); j++) {
				result.add((rows.charAt(i) - '1') * 9 + columns.charAt(j) - '1');
			}
		}
		return toArray(result);
	}

	private static int[] rows(String numbers) {
		Set<Integer> result = new LinkedHashSet<Integer>();
		for (int i = 0; i < numbers.length(); i++) {
			int row = numbers.charAt(i) - '1';
			for (int col = 0; col < 9; col++) {
				result.add(row * 9 + col);
			}
		}
		return toArray(result);
	}

	private static int[] columns(String numbers) {
		Set<Integer> result = new LinkedHashSet<Integer>();
		for (int i = 0; i < numbers.length(); i++) {
			int col = numbers.charAt(i) - '1';
			for (int row = 0; row < 9; row++) {
				result.add(row * 9 + col);
			}
		}
		return toArray(result);
	}

	private static int[] block(int number) {
		Set<Integer> result = new LinkedHashSet<Integer>();
		int block = number - 1;
		int startRow = block / 3 * 3;
		int startCol = block % 3 * 3;
		for (int row = startRow; row < startRow + 3; row++) {
			for (int col = startCol; col < startCol + 3; col++) {
				result.add(row * 9 + col);
			}
		}
		return toArray(result);
	}

	private static int[] blocks(String numbers) {
		Set<Integer> result = new LinkedHashSet<Integer>();
		for (int i = 0; i < numbers.length(); i++) {
			int[] cells = block(numbers.charAt(i) - '0');
			for (int cell : cells) {
				result.add(cell);
			}
		}
		return toArray(result);
	}

	private static int chineseNumber(char number) {
		String digits = "一二三四五六七八九";
		int index = digits.indexOf(number);
		return index == -1 ? number - '0' : index + 1;
	}

	private static int[] toArray(Set<Integer> values) {
		int[] result = new int[values.size()];
		int index = 0;
		for (int value : values) {
			result[index++] = value;
		}
		return result;
	}
}
