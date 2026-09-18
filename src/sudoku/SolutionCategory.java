/*
 * Copyright (C) 2008-12  Bernhard Hobiger
 *
 * This file is part of HoDoKu.
 *
 * HoDoKu is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * HoDoKu is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with HoDoKu. If not, see <http://www.gnu.org/licenses/>.
 */

package sudoku;

/**
 *
 * @author hobiwan
 */
public enum SolutionCategory {
	SINGLES("SolutionCategory.SINGLES"), INTERSECTIONS("SolutionCategory.INTERSECTIONS"),
	SUBSETS("SolutionCategory.SUBSETS"), BASIC_FISH("SolutionCategory.BASIC_FISH"),
	FINNED_BASIC_FISH("SolutionCategory.FINNED_BASIC_FISH"), FRANKEN_FISH("SolutionCategory.FRANKEN_FISH"),
	FINNED_FRANKEN_FISH("SolutionCategory.FINNED_FRANKEN_FISH"), MUTANT_FISH("SolutionCategory.MUTANT_FISH"),
	FINNED_MUTANT_FISH("SolutionCategory.FINNED_MUTANT_FISH"),
	SINGLE_DIGIT_PATTERNS("SolutionCategory.SINGLE_DIGIT_PATTERNS"), COLORING("SolutionCategory.COLORING"),
	UNIQUENESS("SolutionCategory.UNIQUENESS"), CHAINS_AND_LOOPS("SolutionCategory.CHAINS_AND_LOOPS"),
	WINGS("SolutionCategory.WINGS"), ALMOST_LOCKED_SETS("SolutionCategory.ALMOST_LOCKED_SETS"),
	ENUMERATIONS("SolutionCategory.ENUMERATIONS"), MISCELLANEOUS("SolutionCategory.MISCELLANEOUS"),
	LAST_RESORT("SolutionCategory.LAST_RESORT");

	private String resourceKey;
	private String categoryName;

	SolutionCategory() {
		// für XMLEncoder
		resourceKey = null;
	}

	SolutionCategory(String resourceKey) {
		this.resourceKey = resourceKey;
		reloadCategoryName();
	}

	private void reloadCategoryName() {
		if (resourceKey != null) {
			categoryName = java.util.ResourceBundle.getBundle("intl/SolutionCategory").getString(resourceKey);
		}
	}

	/** Reloads all display names after the default locale has changed. */
	public static void resetCategoryNames() {
		for (SolutionCategory category : values()) {
			category.reloadCategoryName();
		}
	}

	@Override
	public String toString() {
		return "enum SolutionCategory: " + categoryName;
	}

	public String getCategoryName() {
		return categoryName;
	}

	public void setCategoryName(String name) {
		categoryName = name;
	}

	public boolean isFish() {
		if (this == BASIC_FISH || this == FINNED_BASIC_FISH || this == FRANKEN_FISH || this == FINNED_FRANKEN_FISH
				|| this == MUTANT_FISH || this == FINNED_MUTANT_FISH) {
			return true;
		}
		return false;
	}
}
