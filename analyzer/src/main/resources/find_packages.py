#!/usr/bin/env python3

# Copyright (C) 2017-2018 HERE Europe B.V.
#
# This program is free software; you can redistribute it and/or modify
# it under the terms of the GNU General Public License version 2 as
# published by the Free Software Foundation.
#
# This program is distributed in the hope that it will be useful,
# but WITHOUT ANY WARRANTY; without even the implied warranty of
# MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
# GNU General Public License for more details.
#
# You should have received a copy of the GNU General Public License along
# with this program; if not, write to the Free Software Foundation, Inc.,
# 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.

import json
import os.path
import sys

bb_lib_path = os.path.abspath(os.path.join(os.environ['BBPATH'], '..', 'bitbake', 'lib'))
sys.path.append(bb_lib_path)

import bb.tinfoil


def parse_recipe(recipe_name, exclude_recipes, tinfoil):
    recipe_info = tinfoil.get_recipe_info(recipe_name)
    recipe = tinfoil.parse_recipe_file(recipe_info.fn, True, tinfoil.get_file_appends(recipe_info.fn))
    depends = {d for d in recipe.getVar('DEPENDS').split()} - exclude_recipes

    # create a new set of excludes instead of mutating the parameter
    exclude_recipes = exclude_recipes.union({recipe_name}, depends)

    return {
        'name': recipe_name,
        'version': recipe_info.pv,
        'license': recipe.getVar('LICENSE'),
        'summary': recipe.getVar('SUMMARY'),
        'description': recipe.getVar('DESCRIPTION'),
        'homepage': recipe.getVar('HOMEPAGE'),
        'src_uri': recipe.getVar('SRC_URI').split(),
        'srcrev': recipe.getVar('SRCREV'),
        'branch': recipe.getVar('BRANCH'),
        'dependencies': [parse_recipe(d, exclude_recipes, tinfoil) for d in depends],
    }


def fatal(msg):
    print(msg)
    sys.exit(1)


def main():
    recipe_names = set(sys.argv[1:])
    if not recipe_names:
        fatal('missing parameter: provide a space-separated list of recipes')

    with bb.tinfoil.Tinfoil() as tinfoil:
        tinfoil.prepare(quiet=3)
        # These are the packages that bitbake assumes are provided by the host
        # system. They do not have recipes, so searching tinfoil for them will
        # not work. Anyway, by nature they are not included in code we release,
        # only used by it.
        assume_provided = set(tinfoil.config_data.getVar('ASSUME_PROVIDED').split())

        unavailable_recipes = recipe_names.intersection(assume_provided)
        if unavailable_recipes:
            unavailable_recipes = '\n'.join(unavailable_recipes)

            fatal('The following packages are assumed to be provided by the host system and therefore no recipe '
                  'information is available for them:\n{}\n'.format(unavailable_recipes))

        recipes = [parse_recipe(rn, assume_provided, tinfoil) for rn in recipe_names - assume_provided]
        print(json.dumps(recipes, indent=2))


if __name__ == "__main__":
    main()
