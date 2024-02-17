# Copyright (C) 2023 The ORT Project Authors (see <https://github.com/oss-review-toolkit/ort/blob/main/NOTICE>)
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#
# SPDX-License-Identifier: Apache-2.0
# License-Filename: LICENSE


import os

import requests
from rich import print

""" Use current GitHub API to check to llst packages
    in registry and remove all but last 3 or custom
    set number of packages.
    Reference: https://docs.github.com/en/rest/packages/packages?apiVersion=2022-11-28#about-github-packages
"""

dry_run: bool = True  # if os.getenv("INPUT_DRY_RUN") == "true" else False
keep = int(os.getenv("INPUT_KEEP"))
org = os.getenv("GITHUB_REPOSITORY_OWNER")
packages = os.getenv("INPUT_PACKAGES").split(",")
token = os.getenv("INPUT_TOKEN")

print(os.getenv("INPUT_DRY_RUN"))
print(packages)

headers = {
    "Accept": "application/vnd.github+json",
    "Authorization": f"Bearer {token}",
    "X-GitHub-Api-Version": "2022-11-28",
}

# Assembly organization packages url string
pkg_url: str = f"https://api.github.com/orgs/{org}/packages"


def remove_packages():
    for package in packages:
        url = f"{pkg_url}/container/{package}/versions?per_page=100"
        response = requests.get(url, headers=headers)

        if response.status_code == 404:
            continue

        # Sort all images on id based.
        images = sorted(response.json(), key=lambda x: x["id"], reverse=False)

        # Slice and remove all
        if len(images) > keep:
            for image in images[keep:]:
                url = f"{pkg_url}/container/{package}/versions/{image['id']}"

                # Never remove latest or non snapshot tagged images
                if restrict_delete_tags(image["metadata"]["container"]["tags"]):
                    print(
                        f":package: Skip latest tagged {package} id {image['id']} tags {image['metadata']['container']['tags']}"
                    )
                    continue

                if not dry_run:
                    response = requests.delete(url, headers=headers)
                    if response.status_code != 204:
                        print(
                            f":cross_mark: Failed to delete package {package} version id {image['id']}."
                        )
                        continue
                print(
                    f":white_heavy_check_mark: Deleted package {package} version id {image['id']}."
                )


def restrict_delete_tags(tags: list) -> bool:
    if not tags:
        return False
    for tag in tags:
        if tag == "latest":
            return True
        elif ".sha." in tag:
            return False
        elif "SNAPSHOT" in tag:
            return False
        else:
            return True
    return False


if __name__ == "__main__":
    remove_packages()
