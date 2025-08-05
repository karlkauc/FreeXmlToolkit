
/*
 * FreeXMLToolkit - Universal Toolkit for XML
 * Copyright (c) Karl Kauc 2025.
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 */

module.exports = {
  theme: {
    extend: {
      colors: {
        'primary-accent': '#0969da',
        'primary-accent-hover': '#0a5ccb',
        'primary-accent-text': 'white',
        'primary-accent-light': '#e7f2fe', // new: for lighten(primary-accent, 50)
        'primary-accent-dark': '#053b78',  // new: for darken(primary-accent, 20)
        'primary-accent-transparent': 'rgba(9, 105, 218, 0.6)', // new: for transparentize(primary-accent, 0.4)
        'background': '#f6f8fa',
        'control-background': '#f5f5f5',
        'control-inner-background': '#ffffff',
        'text-color': '#24292f',
        'text-color-secondary': '#57606a',
        'text-color-muted': '#8c959f',
        'border-color': '#d0d7de',
        'border-color-focused': '#0969da',
        'border-color-subtle': '#e1e4e8',
        'error': '#f64215',
        'dnd-active-border': 'red',
        'file-chooser-background': 'yellow',
        'xml-button-hover-background': '#B2D8F4',
        'comment-background': '#dcd4d4',
        'comment-text': 'teal',
      },
      fontFamily: {
        sans: ['Segoe UI', 'Roboto', 'Helvetica Neue', 'Arial', 'sans-serif'],
      },
      fontSize: {
        'base': '14px',
        'sm': '12px',
      },
      borderRadius: {
        'DEFAULT': '6px',
      },
    },
  },
  plugins: [],
}
